package com.cesarvaliente.isseparating

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.cesarvaliente.isseparating.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Create a new coroutine since repeatOnLifecycle is a suspend function
        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@MainActivity)
                    .windowLayoutInfo(this@MainActivity)
                    .collect { newLayoutInfo ->
                        updateCurrentLayout(newLayoutInfo)
                    }
            }
        }
    }

    /**
     * Update the current layout based on the passed WindowLayoutInfo
     */
    private fun updateCurrentLayout(newLayoutInfo: WindowLayoutInfo) {
        // Add views that represent display features
        for (displayFeature in newLayoutInfo.displayFeatures) {
            val foldFeature = displayFeature as? FoldingFeature
            foldFeature?.let {
                if (it.isSeparating) {
                    val fold = foldPosition(binding.motionlayout, it)
                    with(binding) {
                        if (foldFeature.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                            ConstraintLayout.getSharedValues().fireNewValue(verticalFold.id, 0)
                            ConstraintLayout.getSharedValues().fireNewValue(horizontalFold.id, fold)

                            //Showing/hiding the text views accordingly to what we want to show
                            textViewsVisibility(View.GONE, View.VISIBLE)
                        } else {
                            //VERTICAL
                            ConstraintLayout.getSharedValues().fireNewValue(horizontalFold.id, 0)
                            ConstraintLayout.getSharedValues().fireNewValue(verticalFold.id, fold)

                            //Showing/hiding the text views accordingly to what we want to show
                            textViewsVisibility(View.VISIBLE, View.GONE)
                        }
                    }
                } else {
                    resetSharedValues()
                    textViewsVisibility(View.GONE, View.GONE)
                }
            }
        }
    }

    //Reset the shared values so we can start from scratch again when the Activity is recreated
    private fun resetSharedValues() {
        with(binding) {
            ConstraintLayout.getSharedValues().fireNewValue(verticalFold.id, 0)
            ConstraintLayout.getSharedValues().fireNewValue(horizontalFold.id, 0)
        }
    }

    //Applies the specific visibility to the two different TextViews we use
    private fun textViewsVisibility(
        verticalTextVisibility: Int,
        horizontalTextVisibility: Int
    ) {
        with(binding) {
            verticalText.visibility = verticalTextVisibility
            horizontalText.visibility = horizontalTextVisibility
        }
    }

    //When the Activity is destroyed we do a clean up.
    override fun onDestroy() {
        super.onDestroy()
        resetSharedValues()
    }

    /**
     * Returns the position of the fold relative to the view
     */
    private fun foldPosition(view: View, foldingFeature: FoldingFeature): Int {
        val splitRect = getFeatureBoundsInWindow(foldingFeature, view)
        splitRect?.let {
            return when (foldingFeature.orientation) {
                FoldingFeature.Orientation.HORIZONTAL -> {
                    view.height.minus(splitRect.top)
                }
                FoldingFeature.Orientation.VERTICAL -> {
                    view.width.minus(splitRect.right)
                }
                else -> 0
            }
        }
        return 0
    }

    /**
     * Get the bounds of the display feature translated to the View's coordinate space and current
     * position in the window. This will also include view padding in the calculations.
     */
    private fun getFeatureBoundsInWindow(
        displayFeature: DisplayFeature,
        view: View,
        includePadding: Boolean = true
    ): Rect? {
        // The the location of the view in window to be in the same coordinate space as the feature.
        val viewLocationInWindow = IntArray(2)
        view.getLocationInWindow(viewLocationInWindow)

        // Intersect the feature rectangle in window with view rectangle to clip the bounds.
        val viewRect = Rect(
            viewLocationInWindow[0], viewLocationInWindow[1],
            viewLocationInWindow[0] + view.width, viewLocationInWindow[1] + view.height
        )

        // Include padding if needed
        if (includePadding) {
            viewRect.left += view.paddingLeft
            viewRect.top += view.paddingTop
            viewRect.right -= view.paddingRight
            viewRect.bottom -= view.paddingBottom
        }

        val featureRectInView = Rect(displayFeature.bounds)
        val intersects = featureRectInView.intersect(viewRect)

        // Checks to see if the display feature overlaps with our view at all
        if ((featureRectInView.width() == 0 && featureRectInView.height() == 0) ||
            !intersects
        ) {
            return null
        }

        // Offset the feature coordinates to view coordinate space start point
        featureRectInView.offset(-viewLocationInWindow[0], -viewLocationInWindow[1])

        return featureRectInView
    }
}