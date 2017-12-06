package top.maweihao.weather.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;

import top.maweihao.weather.util.ServiceUtil;
import top.maweihao.weather.util.remoteView.WidgetUtils;

/**
 * Implementation of App Widget functionality.
 */
public class SimpleWeatherWidget extends AppWidgetProvider {


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        ServiceUtil.startWidgetSyncService(context, true);
    }

    @Override
    public void onEnabled(Context context) {
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
        SimpleWidgetConfigureActivity.deleteAllPref(context);
        if (WidgetUtils.hasAnyWidget(context)) {
            ServiceUtil.stopWidgetSyncService(context);
        }
    }
}

