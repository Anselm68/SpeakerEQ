package com.speakereq.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.speakereq.app.data.SpeakerProfile
import com.speakereq.app.databinding.ActivityProfileListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileListBinding
    private lateinit var adapter: ProfileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = getString(R.string.profiles_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        observeProfiles()
    }

    private fun setupRecyclerView() {
        adapter = ProfileAdapter(
            onActivate = { profile -> activateProfile(profile) },
            onDelete = { profile -> confirmDelete(profile) }
        )

        binding.rvProfiles.layoutManager = LinearLayoutManager(this)
        binding.rvProfiles.adapter = adapter
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            SpeakerEqApp.instance.database.profileDao().getAllProfiles().collectLatest { profiles ->
                adapter.submitList(profiles)
                binding.tvEmpty.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
                binding.rvProfiles.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun activateProfile(profile: SpeakerProfile) {
        lifecycleScope.launch {
            SpeakerEqApp.instance.database.profileDao().activateProfile(profile.id)
            Toast.makeText(
                this@ProfileListActivity,
                getString(R.string.profile_activated, profile.profileName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun confirmDelete(profile: SpeakerProfile) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                lifecycleScope.launch {
                    SpeakerEqApp.instance.database.profileDao().delete(profile)
                    Toast.makeText(
                        this@ProfileListActivity,
                        getString(R.string.profile_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
