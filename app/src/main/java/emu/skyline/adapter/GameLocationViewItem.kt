/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright © 2022 Skyline Team and Contributors (https://github.com/skyline-emu/)
 */

package emu.skyline.adapter

import android.view.ViewGroup
import emu.skyline.databinding.GameLocationItemBinding

object GameLocationBindingFactory : ViewBindingFactory {
    override fun createBinding(parent : ViewGroup) = GameLocationItemBinding.inflate(parent.inflater(), parent, false)
}

open class GameLocationViewItem(
    val path : String,
    var onDelete : ((path : String) -> Unit)? = null,
) : GenericListItem<GameLocationItemBinding>() {
    private var holder : GenericViewHolder<GameLocationItemBinding>? = null

    override fun getViewBindingFactory() = GameLocationBindingFactory

    override fun bind(holder : GenericViewHolder<GameLocationItemBinding>, position : Int) {
        this.holder = holder
        val binding = holder.binding

        val dirPath : String = path.replace("%3A", ":").replace("%2F", "/")
        val index : Int = dirPath.lastIndexOf('/')
        val pathFolder : String = dirPath.substring(index + 1)

        binding.pathFolder.text = pathFolder
        binding.path.text = dirPath
        // Make text views selected for marquee to work
        binding.path.isSelected = true
        binding.path.isSelected = true
        binding.path.isSelected = true
        binding.path.isSingleLine = true

        onDelete?.let { onDelete ->
            binding.deleteButton.visibility = ViewGroup.VISIBLE
            binding.deleteButton.setOnClickListener {
                onDelete.invoke(path)
            }
        } ?: run {
            binding.deleteButton.visibility = ViewGroup.GONE
        }
    }

    /**
     * The label of the driver is used as key as it's effectively unique
     */
    override fun key() = path

    override fun areItemsTheSame(other : GenericListItem<GameLocationItemBinding>) : Boolean = key() == other.key()

    override fun areContentsTheSame(other : GenericListItem<GameLocationItemBinding>) : Boolean = other is GameLocationViewItem && path == other.path
}
