// SPDX-License-Identifier: GPL-3.0-or-later
package com.jagger.timer.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class TakeNowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TakeNowWidget()
}
