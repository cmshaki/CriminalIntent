package com.bignerdranch.android.criminalintent

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings.System.DATE_FORMAT
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.text.DateFormat
import java.util.*

private const val ARG_CRIME_ID = "crime_id"
private const val DIALOG_DATE = "DialogDate"
private const val DIALOG_PHOTO = "DialogPhoto"
private const val REQUEST_DATE = "0"
private const val RESULT_DATE = "resultDate"

class CrimeFragment : Fragment() {
    private lateinit var crime: Crime
    private lateinit var titleField: EditText
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var solvedCheckBox: CheckBox
    private lateinit var reportButton: Button
    private lateinit var suspectButton: Button
    private lateinit var callSuspectButton: Button
    private lateinit var photoButton: ImageButton
    private lateinit var photoView: ImageView
    private lateinit var photoFile: File
    private lateinit var photoUri: Uri

    private val crimeDetailViewModel: CrimeDetailViewModel by lazy {
        ViewModelProvider(this).get(CrimeDetailViewModel::class.java)
    }

    private var photoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) {
            updatePhotoView()
            requireActivity().revokeUriPermission(
                photoUri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

    private var contactLauncher =
        registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            // Specify which fields you want your query to return values for
            val queryFields = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
            // Perform your query - the contactUri is like a "where" clause here
            val cursor = requireActivity().contentResolver
                .query(uri, queryFields, null, null, null)
            cursor?.use {
                // Verify cursor contains at least one result
                if (it.count == 0) {
                    return@use
                }

                // Pull out the first column of the first row of data -
                // that is your suspect's name
                it.moveToFirst()
                val suspect = it.getString(0)
                crime.suspect = suspect
                getAndCallSuspect(suspect)
                crimeDetailViewModel.saveCrime(crime)
                suspectButton.text = suspect
                updateUI()
            }
        }

    private fun setPhotoButton() {
        photoButton.apply {
            val packageManager: PackageManager = requireActivity().packageManager
            val captureImage =
                ActivityResultContracts.TakePicture().createIntent(requireActivity(), photoUri)
            val resolvedActivity: ResolveInfo? =
                packageManager.resolveActivity(
                    captureImage,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            if (resolvedActivity == null) {
                isEnabled = false
            }

            setOnClickListener {
                val cameraActivities: List<ResolveInfo> =
                    packageManager.queryIntentActivities(
                        captureImage,
                        PackageManager.MATCH_DEFAULT_ONLY
                    )

                for (cameraActivity in cameraActivities) {
                    requireActivity().grantUriPermission(
                        cameraActivity.activityInfo.packageName,
                        photoUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                photoLauncher.launch(photoUri)
            }
        }
    }

    private fun getAndCallSuspect(suspectName: String) {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val queryFields = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER
        )
        val tel: String;
        val numberCursor = requireActivity().contentResolver
            .query(uri, queryFields, null, null, null)
        numberCursor?.use {
            if (it.count == 0) {
                return@use
            }
            it.moveToFirst()
            var number = ""
            var name = ""
            do {
                name = it.getString(0)
                if (suspectName == name) {
                    number = it.getString(1)
                    break
                }
            } while (it.moveToNext())
            crime.tel = number
        }
        callSuspectButton.isEnabled = crime.tel.isNotEmpty()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crime = Crime()
        val crimeId: UUID = arguments?.getSerializable(ARG_CRIME_ID) as UUID
        crimeDetailViewModel.loadCrime(crimeId)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_crime, container, false)
        titleField = view.findViewById(R.id.crime_title) as EditText
        dateButton = view.findViewById(R.id.crime_date) as Button
        timeButton = view.findViewById(R.id.crime_time) as Button
        solvedCheckBox = view.findViewById(R.id.crime_solved) as CheckBox
        reportButton = view.findViewById(R.id.crime_report) as Button
        suspectButton = view.findViewById(R.id.crime_suspect) as Button
        callSuspectButton = view.findViewById(R.id.call_suspect) as Button
        photoButton = view.findViewById(R.id.crime_camera) as ImageButton
        photoView = view.findViewById(R.id.crime_photo) as ImageView

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        crimeDetailViewModel.crimeLiveData.observe(
            viewLifecycleOwner
        ) { crime ->
            crime?.let {
                this.crime = crime
                photoFile = crimeDetailViewModel.getPhotoFile(crime)
                photoUri = FileProvider.getUriForFile(
                    requireActivity(),
                    "com.bignerdranch.android.criminalintent.fileprovider",
                    photoFile
                )
                setPhotoButton()
                updateUI()
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val titleWatcher = object : TextWatcher {

            override fun beforeTextChanged(
                sequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                // This space intentionally left blank
            }

            override fun onTextChanged(
                sequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                crime.title = sequence.toString()
            }

            override fun afterTextChanged(sequence: Editable?) {
                // This one too
            }
        }

        titleField.addTextChangedListener(titleWatcher)
        solvedCheckBox.apply {
            setOnCheckedChangeListener { _, isChecked ->
                crime.isSolved = isChecked
            }
        }
        dateButton.setOnClickListener {
            setFragmentResultListener(REQUEST_DATE) { _,
                                                      bundle ->
                val result = bundle.getSerializable("resultDate")
                crime.date = result as Date
                updateUI()
            }
            DatePickerFragment.newInstance(crime.date).apply {
                show(this@CrimeFragment.parentFragmentManager, DIALOG_DATE)
            }
        }
        timeButton.setOnClickListener {
            setFragmentResultListener(REQUEST_DATE) { _,
                                                      bundle ->
                val result = bundle.getSerializable(RESULT_DATE)
                crime.date = result as Date
                updateUI()
            }
            TimePickerFragment.newInstance(crime.date).apply {
                show(this@CrimeFragment.parentFragmentManager, DIALOG_DATE)
            }
        }
        reportButton.setOnClickListener {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getCrimeReport())
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    getString(R.string.crime_report_subject)
                )
            }.also { intent ->
                val chooserIntent =
                    Intent.createChooser(intent, getString(R.string.send_report))
                startActivity(chooserIntent)
            }
        }
        suspectButton.apply {
            if (crime.suspect.isNotEmpty()) {
                suspectButton.text = crime.suspect
            }
            val newIntent =
                ActivityResultContracts.PickContact().createIntent(requireContext(), null)
            val packageManager: PackageManager = requireActivity().packageManager
            val resolvedActivity: ResolveInfo? =
                packageManager.resolveActivity(newIntent, PackageManager.MATCH_ALL)
            isEnabled = resolvedActivity != null

            setOnClickListener {
                contactLauncher.launch()
            }
        }

        callSuspectButton.apply {
            setOnClickListener {
                val packageManager: PackageManager = requireActivity().packageManager
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${crime.tel}")
                }

                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            }
        }

        photoView.apply {
            setOnClickListener {
                PhotoViewerFragment.newInstance(photoFile.path).apply {
                    show(this@CrimeFragment.parentFragmentManager, DIALOG_PHOTO)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        crimeDetailViewModel.saveCrime(crime)
    }

    override fun onDetach() {
        super.onDetach()
        requireActivity().revokeUriPermission(
            photoUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    }

    private fun updateUI() {
        titleField.setText(crime.title)
        dateButton.text = DateFormat.getDateInstance(DateFormat.FULL).format(this.crime.date)
        timeButton.text = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(this.crime.date)
        solvedCheckBox.apply {
            isChecked = crime.isSolved
            jumpDrawablesToCurrentState()
        }
        if (crime.suspect.isNotEmpty()) {
            suspectButton.text = crime.suspect
            callSuspectButton.isEnabled = true
        } else {
            callSuspectButton.isEnabled = false
        }
        updatePhotoView()
    }

    private fun updatePhotoView() {
        if (photoFile.exists()) {
            val width = photoView.measuredWidth
            val height = photoView.measuredHeight
            val bitmap = getScaledBitmap(photoFile.path, width, height)
            photoView.apply {
                isEnabled = true
                setImageBitmap(bitmap)
                contentDescription = getString(R.string.crime_photo_image_description)
            }
        } else {
            photoView.apply {
                isEnabled = false
                setImageDrawable(null)
                contentDescription = getString(R.string.crime_photo_no_image_description)
            }
        }
    }

    private fun getCrimeReport(): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString = android.text.format.DateFormat.format(DATE_FORMAT, crime.date).toString()
        val suspect = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(
            R.string.crime_report,
            crime.title, dateString, solvedString, suspect
        )
    }

    companion object {
        fun newInstance(crimeId: UUID): CrimeFragment {
            val args = Bundle().apply {
                putSerializable(ARG_CRIME_ID, crimeId)
            }
            return CrimeFragment().apply {
                arguments = args
            }
        }
    }
}