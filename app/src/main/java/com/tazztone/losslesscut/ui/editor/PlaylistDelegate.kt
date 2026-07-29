package com.tazztone.losslesscut.ui.editor

import android.content.Context
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.tazztone.losslesscut.R
import com.tazztone.losslesscut.ui.MediaClipAdapter
import com.tazztone.losslesscut.ui.PlayerManager
import com.tazztone.losslesscut.ui.RotationManager
import com.tazztone.losslesscut.viewmodel.VideoEditingUiState
import com.tazztone.losslesscut.viewmodel.VideoEditingViewModel

/** Owns playlist binding, drag/reorder behavior, and clip removal confirmation. */
@OptIn(UnstableApi::class)
class PlaylistDelegate(
    private val container: View,
    private val recyclerView: RecyclerView,
    private val viewModel: VideoEditingViewModel,
    private val playerManager: PlayerManager,
    private val rotationManager: RotationManager,
    private val onAddClicked: () -> Unit
) {
    private val context: Context = recyclerView.context
    private lateinit var itemTouchHelper: ItemTouchHelper
    private lateinit var adapter: MediaClipAdapter

    fun setup() {
        itemTouchHelper = ItemTouchHelper(createTouchCallback())
        adapter = MediaClipAdapter(
            onClipSelected = ::selectClip,
            onClipsReordered = ::reorderClips,
            onClipLongPressed = ::confirmRemoveClip,
            onStartDrag = { viewHolder -> itemTouchHelper.startDrag(viewHolder) },
            onAddClicked = onAddClicked
        )
        recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    fun submitList(state: VideoEditingUiState.Success) {
        if (state.clips.size > 1) {
            container.visibility = View.VISIBLE
            adapter.submitList(state.clips)
            adapter.updateSelection(state.selectedClipIndex)
        } else {
            container.visibility = View.GONE
        }
    }

    fun updateSelection(index: Int) {
        if (::adapter.isInitialized) adapter.updateSelection(index)
    }

    private fun selectClip(index: Int) {
        val currentState = viewModel.uiState.value as? VideoEditingUiState.Success
        if (currentState != null && index != currentState.selectedClipIndex) {
            rotationManager.setRotation(0, animate = false)
            viewModel.selectClip(index)
        }
    }

    private fun reorderClips(from: Int, to: Int) {
        viewModel.reorderClips(from, to)
        playerManager.moveMediaItem(from, to)
    }

    private fun confirmRemoveClip(index: Int) {
        (viewModel.uiState.value as? VideoEditingUiState.Success)
            ?.clips?.getOrNull(index) ?: return

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.delete))
            .setMessage(context.getString(R.string.remove_clip_confirm))
            .setPositiveButton(context.getString(R.string.delete)) { _, _ ->
                viewModel.removeClip(index)
                playerManager.removeMediaItem(index)
            }
            .setNegativeButton(context.getString(R.string.cancel), null)
            .show()
    }

    private fun createTouchCallback() = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            adapter.moveItemVisual(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                adapter.startDrag(viewHolder.bindingAdapterPosition)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            adapter.commitPendingMove(viewHolder.bindingAdapterPosition)
        }

        override fun isLongPressDragEnabled(): Boolean = false
    }
}
