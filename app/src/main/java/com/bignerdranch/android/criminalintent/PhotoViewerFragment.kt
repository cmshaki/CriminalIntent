package com.bignerdranch.android.criminalintent

import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog.Builder
import androidx.fragment.app.DialogFragment

private const val ARG_PHOTO_FILE = "photo"
private const val TAG = "CrimeFragment"

class PhotoViewerFragment : DialogFragment() {
    private lateinit var imageView: ImageView;
    private lateinit var path: String;

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        path = arguments?.getSerializable(ARG_PHOTO_FILE) as String
        val inflater: LayoutInflater = requireActivity().layoutInflater

        val view: View = inflater.inflate(R.layout.fragment_photo_viewer, null)

        val builder = Builder(requireActivity())

        imageView = view.findViewById(R.id.crime_photo_viewer) as ImageView

        imageView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                p0: View?,
                p1: Int,
                p2: Int,
                p3: Int,
                p4: Int,
                p5: Int,
                p6: Int,
                p7: Int,
                p8: Int
            ) {
                imageView.removeOnLayoutChangeListener(this)
                val width = view.measuredWidth
                val height = view.measuredHeight
                imageView.setImageBitmap(getScaledBitmap(path, width, height))
            }
        })

        builder.setView(view)
        return builder.create()
    }

    companion object {
        fun newInstance(path: String): DialogFragment {
            val args = Bundle().apply {
                putSerializable(ARG_PHOTO_FILE, path)
            }

            return PhotoViewerFragment().apply {
                arguments = args
            }
        }
    }
}