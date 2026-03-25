package com.speakereq.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.speakereq.app.data.SpeakerProfile
import com.speakereq.app.databinding.ItemProfileBinding
import java.text.SimpleDateFormat
import java.util.*

class ProfileAdapter(
    private val onActivate: (SpeakerProfile) -> Unit,
    private val onDelete: (SpeakerProfile) -> Unit
) : ListAdapter<SpeakerProfile, ProfileAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(private val binding: ItemProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: SpeakerProfile) {
            binding.tvProfileName.text = profile.profileName
            binding.tvDeviceName.text = profile.deviceName

            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            binding.tvCreatedAt.text = itemView.context.getString(
                R.string.profile_created,
                dateFormat.format(Date(profile.createdAt))
            )

            binding.viewActiveIndicator.visibility =
                if (profile.isActive) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onActivate(profile) }
            binding.btnDelete.setOnClickListener { onDelete(profile) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProfileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SpeakerProfile>() {
            override fun areItemsTheSame(a: SpeakerProfile, b: SpeakerProfile) = a.id == b.id
            override fun areContentsTheSame(a: SpeakerProfile, b: SpeakerProfile) = a == b
        }
    }
}
