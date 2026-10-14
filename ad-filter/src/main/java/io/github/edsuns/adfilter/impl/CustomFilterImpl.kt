package io.github.edsuns.adfilter.impl

import io.github.edsuns.adfilter.CustomFilter
import io.github.edsuns.adfilter.util.RuleIterator

/**
 * Created by Edsuns@qq.com on 2021/7/29.
 */
internal class CustomFilterImpl constructor(
    private val filterDataLoader: FilterDataLoader,
    data: String? = null,
) : RuleIterator(data),
    CustomFilter {
    override fun flush() {
        val blacklistStr = dataBuilder.toString()
        if (blacklistStr.isNotBlank()) {
            val rawData = blacklistStr.toByteArray()
            filterDataLoader.loadCustomFilter(rawData)
        } else {
            filterDataLoader.remove(FilterDataLoader.ID_CUSTOM)
            filterDataLoader.unloadCustomFilter()
        }
    }
}
