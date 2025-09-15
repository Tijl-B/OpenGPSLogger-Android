package eu.tijlb.opengpslogger.ui.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import eu.tijlb.opengpslogger.databinding.FragmentBoundingBoxSelectorBinding
import eu.tijlb.opengpslogger.model.dto.BBoxDto

private const val TAG = "ogl-boundingboxselectorfragment"

class BoundingBoxSelectorFragment : Fragment() {

    var callback: (BBoxDto) -> Unit = {}
    private var _binding: FragmentBoundingBoxSelectorBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBoundingBoxSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonConfirm.setOnClickListener {
            val bbox = binding.viewLayeredMap.boundingBox()
            Log.d(TAG, "User selected bbox $bbox")
            callback(bbox)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
