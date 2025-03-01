package cn.wankkoree.xposed.enablewebviewdebugging.activity

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.icu.text.Collator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.ActivityOptionsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.wankkoree.xposed.enablewebviewdebugging.R
import cn.wankkoree.xposed.enablewebviewdebugging.activity.component.Tag
import cn.wankkoree.xposed.enablewebviewdebugging.data.AppSP
import cn.wankkoree.xposed.enablewebviewdebugging.data.AppsSP
import cn.wankkoree.xposed.enablewebviewdebugging.data.getSet
import cn.wankkoree.xposed.enablewebviewdebugging.databinding.AppsBinding
import com.highcapable.yukihookapi.hook.factory.modulePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Apps : AppCompatActivity() {
    private lateinit var viewBinding: AppsBinding
    private var toast: Toast? = null
    private val appResultContract = registerForActivityResult(AppResultContract()) {
        adapter.update(it)
    }

    lateinit var adapter: AppListItemAdapter
    private var isSearching = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = AppsBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.appsLoading.setColorSchemeColors(getColor(R.color.backgroundSuccess), getColor(R.color.backgroundInfo), getColor(R.color.backgroundError))
        viewBinding.appsList.layoutManager = LinearLayoutManager(this)
        adapter = AppListItemAdapter(lifecycleScope)
        viewBinding.appsList.adapter = adapter
        refresh()

        viewBinding.appsToolbarBack.setOnClickListener {
            if (isSearching) {
                viewBinding.appsToolbarSearchBtn.callOnClick()
            } else {
                finish()
            }
        }
        viewBinding.appsToolbarSearch.doAfterTextChanged {
            with(modulePrefs("apps")) {
                adapter.filter(get(AppsSP.show_system_app), get(AppsSP.show_no_network), isSearching, it.toString(), true)
            }
        }
        viewBinding.appsToolbarSearch.setOnFocusChangeListener { it, b ->
            val imm: InputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            if (b)
                imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
            else
                imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        viewBinding.appsToolbarSearchBtn.setOnClickListener {
            if (!isSearching) {
                viewBinding.appsToolbarName.visibility = View.GONE
                viewBinding.appsToolbarSearchBtn.visibility = View.GONE
                viewBinding.appsToolbarSearch.visibility = View.VISIBLE
                viewBinding.appsToolbarSearch.requestFocus()
                isSearching = true
                with(modulePrefs("apps")) {
                    adapter.filter(get(AppsSP.show_system_app), get(AppsSP.show_no_network), isSearching, viewBinding.appsToolbarSearch.text.toString(), true)
                }
            } else {
                viewBinding.appsToolbarSearch.visibility = View.GONE
                viewBinding.appsToolbarName.visibility = View.VISIBLE
                viewBinding.appsToolbarSearchBtn.visibility = View.VISIBLE
                isSearching = false
                with(modulePrefs("apps")) {
                    adapter.filter(get(AppsSP.show_system_app), get(AppsSP.show_no_network), isSearching, viewBinding.appsToolbarSearch.text.toString(), true)
                }
            }
        }
        viewBinding.appsToolbarMenu.setOnClickListener {
            PopupMenu(this, it).apply {
                menuInflater.inflate(R.menu.apps_toolbar_menu, menu)
                with(modulePrefs("apps")) {
                    menu.findItem(R.id.apps_toolbar_menu_show_system_app).isChecked = get(AppsSP.show_system_app)
                    menu.findItem(R.id.apps_toolbar_menu_show_no_network).isChecked = get(AppsSP.show_no_network)
                }
                setOnMenuItemClickListener {
                    when (it.itemId) {
                        R.id.apps_toolbar_menu_refresh ->  refresh()
                        R.id.apps_toolbar_menu_show_system_app -> with(modulePrefs("apps")) {
                            it.isChecked = !it.isChecked
                            put(AppsSP.show_system_app, it.isChecked)
                            adapter.filter(get(AppsSP.show_system_app), get(AppsSP.show_no_network), isSearching, viewBinding.appsToolbarSearch.text.toString(), true)
                        }
                        R.id.apps_toolbar_menu_show_no_network -> with(modulePrefs("apps")) {
                            it.isChecked = !it.isChecked
                            put(AppsSP.show_no_network, it.isChecked)
                            adapter.filter(get(AppsSP.show_system_app), get(AppsSP.show_no_network), isSearching, viewBinding.appsToolbarSearch.text.toString(), true)
                        }
                    }
                    true
                }
            }.show()
        }
        viewBinding.appsLoading.setOnRefreshListener {
            refresh()
        }
    }

    override fun onBackPressed() {
        if (isSearching) {
            viewBinding.appsToolbarSearchBtn.callOnClick()
        } else {
            super.onBackPressed()
        }
    }

    private fun refresh() {
        viewBinding.appsToolbarSearch.isEnabled = false
        viewBinding.appsToolbarSearchBtn.isEnabled = false
        viewBinding.appsToolbarMenu.isEnabled = false
        viewBinding.appsLoading.isRefreshing = true
        lifecycleScope.launch(Dispatchers.IO) {
            val appList = mutableListOf<AppListItemAdapter.AppListItem>()
            val apps = packageManager.getInstalledPackages(PackageManager.GET_META_DATA or PackageManager.GET_PERMISSIONS)
            for (app in apps) {
                val appItem = with(this@Apps.modulePrefs("apps_${app.packageName}")) {
                    val hooks = getSet(AppSP.hooks)
                    AppListItemAdapter.AppListItem(
                        app.applicationInfo.loadIcon(packageManager),
                        app.applicationInfo.loadLabel(packageManager) as String,
                        app.versionName ?: "",
                        app.versionCode,
                        app.packageName,
                        app.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 1,
                        app.requestedPermissions == null || !app.requestedPermissions.contains("android.permission.INTERNET"),
                        !app.applicationInfo.enabled,
                        get(AppSP.is_enabled),
                        hooks.size,
                    )
                }
                appList.add(appItem)
            }
            lifecycleScope.launch(Dispatchers.Main) {
                with(modulePrefs("apps")) {
                    adapter.init(
                        appList.sortedWith(
                            compareByDescending<AppListItemAdapter.AppListItem>{it.isEnabled}
                                .thenByDescending{it.ruleNumbers}
                                .thenBy{it.isSystemApp}
                                .thenBy(Collator.getInstance()){it.name}
                        ),
                        get(AppsSP.show_system_app), get(AppsSP.show_no_network), isSearching, viewBinding.appsToolbarSearch.text.toString()
                    )
                }
                viewBinding.appsToolbarSearch.isEnabled = true
                viewBinding.appsToolbarSearchBtn.isEnabled = true
                viewBinding.appsToolbarMenu.isEnabled = true
                viewBinding.appsLoading.isRefreshing = false
            }
        }
    }

    class AppListItemAdapter(private val lifecycleScope: LifecycleCoroutineScope) : RecyclerView.Adapter<AppListItemAdapter.ViewHolder>() {
        private var context: Apps? = null
        private var rawData: List<AppListItemAdapter.AppListItem> = emptyList()
        private val filteredData: MutableList<AppListItemAdapter.AppListItem> = mutableListOf()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            context = parent.context as Apps
            val view = LayoutInflater.from(context).inflate(R.layout.component_applistitem, parent, false)
            return ViewHolder(view)
        }

        fun init(data: List<AppListItemAdapter.AppListItem>, showSystemApp: Boolean, showNoNetwork: Boolean, isSearching: Boolean, searchText: String) {
            rawData = data.toList()
            val oldFilteredData = filteredData.toList()
            filteredData.clear()
            filter(showSystemApp, showNoNetwork, isSearching, searchText, false)

            object: DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldFilteredData.size
                override fun getNewListSize(): Int = filteredData.size
                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldFilteredData[oldItemPosition]
                    val newItem = filteredData[newItemPosition]
                    return oldItem.pkg == newItem.pkg
                }
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = oldFilteredData[oldItemPosition]
                    val newItem = filteredData[newItemPosition]
                    return oldItem.icon == newItem.icon &&
                            oldItem.name == newItem.name &&
                            oldItem.versionName == newItem.versionName &&
                            oldItem.versionCode == newItem.versionCode &&
                            oldItem.ruleNumbers == newItem.ruleNumbers &&
                            oldItem.isSystemApp == newItem.isSystemApp &&
                            oldItem.isNoNetwork == newItem.isNoNetwork
                }
//                override fun getChangePayload(oldItemPosition: Int, newItemPosition: Int): Any? {
//                    //TODO: 完全实现局部刷新
//                }
            }.let {
                DiffUtil.calculateDiff(it)
            }.dispatchUpdatesTo(this)
        }

        fun filter(showSystemApp: Boolean, showNoNetwork: Boolean, isSearching: Boolean, searchText: String, partialRefresh: Boolean = true) {
            var p = 0
            for (raw in rawData) {
                val canShow = (
                        !raw.isSystemApp && !raw.isNoNetwork ||
                        raw.isSystemApp && !raw.isNoNetwork && showSystemApp ||
                        !raw.isSystemApp && raw.isNoNetwork && showNoNetwork ||
                        raw.isSystemApp && raw.isNoNetwork && showSystemApp && showNoNetwork ||
                        raw.isEnabled
                        ) && (
                        !isSearching || isSearching && (raw.pkg.contains(searchText, true) || raw.name.contains(searchText, true))
                        )
                if (p >= filteredData.size || raw.pkg != filteredData[p].pkg) {
                    if (canShow) {
                        filteredData.add(p, raw)
                        if (partialRefresh) {
                            notifyItemInserted(p)
                        }
                        p++
                    }
                } else {
                    if (!canShow) {
                        filteredData.removeAt(p)
                        if (partialRefresh) {
                            notifyItemRemoved(p)
                        }
                    } else {
                        p++
                    }
                }
            }
        }

        override fun getItemCount(): Int = filteredData.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.iconView.setImageDrawable(filteredData[position].icon)
            holder.iconView.contentDescription = filteredData[position].name
            holder.iconView.drawable.mutate().colorFilter = if (filteredData[position].isEnabled) null else grayColorFilter
            holder.nameView.text = filteredData[position].name
            holder.nameView.paintFlags =
                if (filteredData[position].isFreezed)
                    holder.nameView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else
                    holder.nameView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.versionView.text = context!!.getString(R.string.version_format).format(filteredData[position].versionName, filteredData[position].versionCode)
            holder.packageView.text = filteredData[position].pkg
            holder.stateView.text = context!!.getString(R.string.applistitem_num).format(context!!.getString(if (filteredData[position].isEnabled) R.string.enabled else R.string.disabled), filteredData[position].ruleNumbers)
            holder.isSystemAppView.color = context!!.getColor(if (!filteredData[position].isSystemApp) R.color.backgroundSuccess else R.color.backgroundError)
            holder.isSystemAppView.text = context!!.getString(if (!filteredData[position].isSystemApp) R.string.user_application else R.string.system_application)
            holder.isNoNetworkView.color = context!!.getColor(if (!filteredData[position].isNoNetwork) R.color.backgroundSuccess else R.color.backgroundError)
            holder.isNoNetworkView.text = context!!.getString(if (!filteredData[position].isNoNetwork) R.string.need_network else R.string.no_network)

            holder.itemView.setOnClickListener {
                val p = filteredData.indexOfFirst { al ->
                    al.pkg == (it.findViewById(R.id.component_applistitem_package) as TextView).text
                }
                val intent = Intent(context, App::class.java)
                intent.putExtra(Intent.EXTRA_PACKAGE_NAME, filteredData[p].pkg)
                intent.putExtra("p", p)
                context!!.appResultContract.launch(intent, ActivityOptionsCompat.makeSceneTransitionAnimation(context!!,
                    androidx.core.util.Pair(holder.iconView, "targetAppIcon"),
                    androidx.core.util.Pair(holder.nameView, "targetAppName"),
                    androidx.core.util.Pair(holder.versionView, "targetAppVersion"),
                    androidx.core.util.Pair(holder.packageView, "targetAppPackage"),
                ))
            }
        }

        fun update(p: Int) {
            with(context!!.modulePrefs("apps_${filteredData[p].pkg}")) {
                val hooks = getSet(AppSP.hooks)
                filteredData[p].isEnabled = get(AppSP.is_enabled)
                filteredData[p].ruleNumbers = hooks.size
            }
            notifyItemChanged(p)
        }

        data class AppListItem (
            val icon: Drawable,
            val name: String,
            val versionName: String,
            val versionCode: Int,
            val pkg: String,
            val isSystemApp: Boolean,
            val isNoNetwork: Boolean,
            val isFreezed: Boolean,
            var isEnabled: Boolean,
            var ruleNumbers: Int,
        )

        inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
            val iconView: ImageView = view.findViewById(R.id.component_applistitem_icon)
            val nameView: TextView = view.findViewById(R.id.component_applistitem_name)
            val versionView: TextView = view.findViewById(R.id.component_applistitem_version)
            val packageView: TextView = view.findViewById(R.id.component_applistitem_package)
            val stateView: TextView = view.findViewById(R.id.component_applistitem_state)
            val isSystemAppView: Tag = view.findViewById(R.id.component_applistitem_is_system_app)
            val isNoNetworkView: Tag = view.findViewById(R.id.component_applistitem_is_no_network)
        }
    }

    class AppResultContract : ActivityResultContract<Intent, Int>() {
        var p = 0
        override fun createIntent(context: Context, input: Intent): Intent {
            return input.also {
                p = it.getIntExtra("p", -1)
                it.removeExtra("p")
            }
        }
        override fun parseResult(resultCode: Int, intent: Intent?): Int {
            return p
        }
    }
}