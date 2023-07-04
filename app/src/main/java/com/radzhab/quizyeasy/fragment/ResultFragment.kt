package com.radzhab.quizyeasy.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.radzhab.quizyeasy.R
import com.radzhab.quizyeasy.databinding.FragmentResultBinding

class ResultFragment : Fragment() {

    lateinit var binding: FragmentResultBinding
    lateinit var correctAns: TextView
    lateinit var totalAns: TextView
    lateinit var performance: TextView
    lateinit var output: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        correctAns=binding.correctAns
        totalAns=binding.totalAns
        performance=binding.performance
        output=binding.output

        val bundle = arguments
        val correctAnsNo= bundle?.getString("correct")
        val totalAnsNo= bundle?.getString("total")
        correctAns.text=correctAnsNo
        totalAns.text=totalAnsNo

        val percentage= (correctAnsNo?.toFloat()?.div(totalAnsNo?.toFloat()!!))?.times(100)

        if (percentage != null) {
            when {
                50 <= percentage && percentage <= 99 -> {
                    performance.text=getString(R.string.good)
                    output.background= context?.let { ContextCompat.getDrawable(it,R.drawable.option_bg) }
                }
                percentage>=100 -> {
                    performance.text=getString(R.string.excellent)
                    output.background=context?.let { ContextCompat.getDrawable(it,R.drawable.right_bg)}
                }
                percentage<50 -> {
                    performance.text=getString(R.string.bad)
                    output.background=context?.let { ContextCompat.getDrawable(it,R.drawable.wrong_bg)}
                }
            }
        }
    }
}