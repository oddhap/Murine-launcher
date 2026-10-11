package app.murinelauncher.widget.appinfo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.LruCache
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.murinelauncher.icons.IconPackManager
import android.icu.text.Collator
import android.icu.text.RuleBasedCollator
import android.icu.text.SearchIterator
import android.icu.text.StringSearch
import app.murinelauncher.widget.radio.RadioGroupBottomSheet
import java.text.StringCharacterIterator
import com.android.launcher3.R
import com.android.launcher3.util.Executors
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet that lets the user pick a specific drawable from an icon pack
 * as the per-app icon override, with a selector for every installed pack.
 *
 * Icons the pack associates with the app itself (appfilter entries for its package)
 * are listed first, followed by the categories supplied by the pack.
 * Listing follows [IconPackManager]'s cache policy: the globally selected pack goes
 * through the primary cache, any other pack is parsed volatilely.
 */
class IconPickerBottomSheet : BottomSheetDialogFragment() {

    private var loadGeneration = 0

    fun configure(packPackage: String, title: CharSequence, componentKey: String) {
        arguments = Bundle().apply {
            putString(KEY_PACK, packPackage)
            putString(KEY_COMPONENT, componentKey)
            putCharSequence(KEY_TITLE, title)
        }
    }

    override fun getTheme(): Int =
        com.android.settingslib.widget.theme.R.style.Theme_SettingsLib_BottomSheetDialog

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.icon_picker_bottom_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: run { dismissAllowingStateLoss(); return }
        val componentKey = args.getString(KEY_COMPONENT) ?: run { dismissAllowingStateLoss(); return }
        view.findViewById<TextView>(R.id.sheet_title).text = args.getCharSequence(KEY_TITLE)
        dialog?.setOnShowListener { RadioGroupBottomSheet.applyAdjustedBackgroundTint(dialog) }
        val ctx = view.context
        val appCtx = ctx.applicationContext
        val grid = view.findViewById<RecyclerView>(R.id.icon_grid)
        val search = view.findViewById<EditText>(R.id.search_bar)
        val status = view.findViewById<TextView>(R.id.icon_picker_status)
        var currentAdapter: IconGridAdapter? = null

        fun pick(pack: String?, drawable: String? = null) {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                putString(KEY_COMPONENT, componentKey)
                putString(KEY_PACK, pack)
                putString(KEY_DRAWABLE, drawable)
            })
            dismiss()
        }
        view.findViewById<View>(R.id.icon_reset_default).setOnClickListener { pick(null) }
        view.findViewById<View>(R.id.icon_use_system).setOnClickListener { pick(IconPackManager.SYSTEM_ICON_PACK) }

        fun loadPack(pack: String) {
            args.putString(KEY_PACK, pack)
            val generation = ++loadGeneration
            status.setText(R.string.icon_picker_loading)
            status.visibility = View.VISIBLE
            val adapter = IconGridAdapter(ctx, pack) { name -> pick(pack, name) }
            currentAdapter = adapter
            grid.layoutManager = GridLayoutManager(ctx, SPAN_COUNT).also { glm ->
                glm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (adapter.isHeader(position)) SPAN_COUNT else 1
                }
            }
            grid.adapter = adapter
            var loaded = false
            var hasIcons = false
            adapter.onRowsChanged = { count ->
                if (loaded) {
                    status.visibility = if (count == 0) View.VISIBLE else View.GONE
                    status.setText(if (hasIcons) R.string.icon_picker_no_results else R.string.icon_picker_empty)
                }
            }
            val usePackDefault = view.findViewById<View>(R.id.icon_use_pack_default)
            usePackDefault.isEnabled = false
            usePackDefault.setOnClickListener { pick(pack) }
            Executors.MODEL_EXECUTOR.execute {
                val hasMappedIcon = IconPackManager.hasIconForComponent(appCtx, pack, componentKey)
                val res = IconPackManager.getPackResources(appCtx, pack)
                fun valid(name: String) = res != null && res.getIdentifier(name, "drawable", pack) != 0
                val appIcons = IconPackManager.getPackIconsForApp(appCtx, pack, componentKey).filter(::valid)
                val suggested = appIcons.toSet()
                val sections = IconPackManager.listPackCategories(appCtx, pack).mapNotNull { (title, icons) ->
                    val validIcons = icons.filter { it !in suggested && valid(it) }
                    if (validIcons.isEmpty()) null else title to validIcons
                }
                Executors.MAIN_EXECUTOR.execute publish@{
                    if (this.view !== view || generation != loadGeneration) return@publish
                    usePackDefault.isEnabled = hasMappedIcon && res != null
                    loaded = true
                    hasIcons = appIcons.isNotEmpty() || sections.isNotEmpty()
                    adapter.filter(search.text.toString())
                    adapter.submit(appIcons, sections)
                }
            }
        }

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { currentAdapter?.filter(s?.toString().orEmpty()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        val packs = IconPackManager.getInstalledPacks(ctx).filter { it.packageName != IconPackManager.SYSTEM_ICON_PACK }
        val selector = view.findViewById<Spinner>(R.id.icon_pack_selector)
        if (packs.isEmpty()) {
            view.findViewById<View>(R.id.icon_use_pack_default).visibility = View.GONE
            selector.visibility = View.GONE
            search.visibility = View.GONE
            grid.visibility = View.GONE
            status.setText(R.string.icon_picker_no_packs)
            return
        }
        selector.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, packs.map { it.label }).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        val initialIndex = packs.indexOfFirst { it.packageName == args.getString(KEY_PACK) }.coerceAtLeast(0)
        selector.setSelection(initialIndex)
        loadPack(packs[initialIndex].packageName)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, item: View?, position: Int, id: Long) {
                val pack = packs[position].packageName
                if (pack != args.getString(KEY_PACK)) loadPack(pack)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        ++loadGeneration
        super.onDestroyView()
    }

    /**
     * Grid adapter: optional "From this app" section, separator header, then all pack icons.
     * Icons are inflated and rasterized to cell-sized bitmaps off the main thread (in parallel),
     * kept only in a sheet-local [LruCache] (volatile; never touches [IconPackManager]'s caches).
     */
    private class IconGridAdapter(context: Context, private val packPackage: String, private val onClick: (String) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        /** A row is either a section header ([header] set) or an icon cell ([icon] set). */
        private class Row(val header: CharSequence? = null, val icon: String? = null)

        private val appCtx = context.applicationContext
        private val iconDpi = context.resources.configuration.densityDpi
        /** Resolved once per sheet; each per-icon load would otherwise cost a PM IPC. */
        private val packRes by lazy { IconPackManager.getPackResources(appCtx, packPackage) }
        private val iconSizePx = dp(context, CELL_SIZE_DP - 2 * CELL_PADDING_DP)
        private val bitmapCache = LruCache<String, Bitmap>(CACHE_SIZE)
        private var appIcons: List<String> = emptyList()
        private var allSections: List<Pair<String, List<String>>> = emptyList()
        private var query = ""
        /**
         * Collator for permissive search.
         */
        private val collator = (Collator.getInstance() as? RuleBasedCollator)?.apply {
            strength = Collator.PRIMARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
            setAlternateHandlingShifted(true)
        }
        private var rows: List<Row> = emptyList()

        var onRowsChanged: ((Int) -> Unit)? = null

        fun isHeader(position: Int): Boolean = rows[position].header != null

        fun submit(app: List<String>, sections: List<Pair<String, List<String>>>) {
            appIcons = app
            allSections = sections
            rebuild()
        }

        fun filter(q: String) {
            query = q
            rebuild()
        }

        /** New collation search for the current query, or null when nothing should be filtered. */
        private fun newSearch(): StringSearch? {
            if (query.isEmpty() || collator == null) return null
            return try {
                StringSearch(query, StringCharacterIterator(query), collator)
            } catch (_: Exception) {
                null
            }
        }

        private fun List<String>.filterQuery(search: StringSearch?): List<String> {
            if (query.isEmpty()) return this
            if (search == null) return filter { it.contains(query, ignoreCase = true) }
            return filter { name ->
                search.setTarget(StringCharacterIterator(name))
                search.first() != SearchIterator.DONE
            }
        }

        private fun rebuild() {
            val search = newSearch()
            val app = appIcons.filterQuery(search)
            rows = buildList {
                if (app.isNotEmpty()) {
                    add(Row(header = appCtx.getString(R.string.suggested_widgets_header_title)))
                    app.forEach { add(Row(icon = it)) }
                }
                allSections.forEach { (section, icons) ->
                    val filtered = icons.filterQuery(search)
                    if (filtered.isNotEmpty()) {
                        add(Row(header = section))
                        filtered.forEach { add(Row(icon = it)) }
                    }
                }
            }
            notifyDataSetChanged()
            onRowsChanged?.invoke(rows.size)
        }

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int =
            if (isHeader(position)) TYPE_HEADER else TYPE_ICON

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            return if (viewType == TYPE_HEADER) {
                val tv = TextView(ctx).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setPadding(dp(ctx, 8), dp(ctx, 16), dp(ctx, 8), dp(ctx, 8))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setTextColor(ContextCompat.getColor(ctx,
                        com.android.settingslib.widget.theme.R.color.settingslib_materialColorPrimary))
                }
                object : RecyclerView.ViewHolder(tv) {}
            } else {
                val iv = ImageView(ctx).apply {
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, CELL_SIZE_DP))
                    val pad = dp(ctx, CELL_PADDING_DP)
                    setPadding(pad, pad, pad, pad)
                    setBackgroundResource(R.drawable.rounded_popup_ripple)
                }
                object : RecyclerView.ViewHolder(iv) {}
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (row.header != null) {
                (holder.itemView as TextView).text = row.header
                return
            }
            val name = row.icon ?: return
            val iv = holder.itemView as ImageView
            iv.contentDescription = name
            iv.setOnClickListener { onClick(name) }
            iv.tag = name
            val cached = bitmapCache.get(name)
            iv.isEnabled = cached != null
            if (cached != null) {
                iv.setImageBitmap(cached)
                return
            }
            iv.setImageBitmap(null)
            Executors.THREAD_POOL_EXECUTOR.execute {
                val bmp = runCatching { renderIcon(name) }.getOrNull()
                if (bmp != null) {
                    bitmapCache.put(name, bmp)
                    Executors.MAIN_EXECUTOR.execute {
                        if (iv.tag == name) {
                            iv.setImageBitmap(bmp)
                            iv.isEnabled = true
                        }
                    }
                } else {
                    // Unresolvable names are removed.
                    Executors.MAIN_EXECUTOR.execute { removeIcon(name) }
                }
            }
        }

        /** Inflates and rasterizes a pack icon to cell size. */
        private fun renderIcon(name: String): Bitmap? {
            val d = packRes?.let { IconPackManager.loadDrawableFromPack(it, packPackage, name, iconDpi) } ?: return null
            val bmp = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, iconSizePx, iconSizePx)
            d.draw(Canvas(bmp))
            return bmp
        }

        /**
         * Drops a drawable that exists in the manifest but fails to render.
         */
        private fun removeIcon(name: String) {
            val inApp = name in appIcons
            val inSections = allSections.any { name in it.second }
            if (!inApp && !inSections) return // already removed by another render
            if (inApp) appIcons = appIcons - name
            if (inSections) {
                allSections = allSections.mapNotNull { (title, icons) ->
                    if (name !in icons) title to icons
                    else (title to (icons - name)).takeIf { it.second.isNotEmpty() }
                }
            }
            rebuild()
        }

        private fun dp(ctx: Context, dp: Int): Int = (dp * ctx.resources.displayMetrics.density).toInt()

        companion object {
            const val TYPE_HEADER = 0
            const val TYPE_ICON = 1
            const val CELL_SIZE_DP = 64
            const val CELL_PADDING_DP = 10
            const val CACHE_SIZE = 256
        }
    }

    companion object {
        const val TAG = "IconPickerBottomSheet"
        private const val SPAN_COUNT = 4
        const val RESULT_KEY = "murine_icon_picked"
        const val KEY_PACK = "icon_pack"
        const val KEY_COMPONENT = "component"
        const val KEY_DRAWABLE = "drawable"
        private const val KEY_TITLE = "title"
    }
}
