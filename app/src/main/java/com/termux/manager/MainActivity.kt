package com.termux.manager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.termux.manager.data.fs.SafBackend
import com.termux.manager.databinding.ActivityMainBinding
import com.termux.manager.ui.BrowserViewModel
import com.termux.manager.ui.FileAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: BrowserViewModel by viewModels()
    private val backend by lazy { SafBackend(applicationContext) }
    private lateinit var adapter: FileAdapter

    private val openTree = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        val isPrimaryRoot = runCatching {
            DocumentsContract.getTreeDocumentId(uri) == "primary:"
        }.getOrDefault(false)
        if (!isPrimaryRoot) {
            Toast.makeText(this, R.string.grant_need_root, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        showBrowser()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = FileAdapter { entry ->
            if (entry.isDir) vm.enter(entry.ref)
            else Toast.makeText(this, entry.name, Toast.LENGTH_SHORT).show()
        }
        binding.fileList.layoutManager = LinearLayoutManager(this)
        binding.fileList.adapter = adapter

        binding.grantBtn.setOnClickListener {
            openTree.launch(SafBackend.PRIMARY_ROOT_TREE_URI)
        }

        vm.entries.observe(this) { adapter.submit(it) }
        vm.path.observe(this) { binding.pathBar.text = it }
        vm.error.observe(this) { msg ->
            if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        onBackPressedDispatcher.addCallback(this) {
            if (!vm.goUp()) {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        if (backend.hasPrimaryRootAccess()) showBrowser() else showOnboarding()
    }

    private fun showOnboarding() {
        binding.onboarding.visibility = View.VISIBLE
        binding.browser.visibility = View.GONE
    }

    private fun showBrowser() {
        binding.onboarding.visibility = View.GONE
        binding.browser.visibility = View.VISIBLE
        vm.openWorkspace()
    }
}
