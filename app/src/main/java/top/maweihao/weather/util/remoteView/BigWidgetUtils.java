package top.maweihao.weather.util.remoteView;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.AlarmClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.GregorianCalendar;

import top.maweihao.weather.R;
import top.maweihao.weather.activity.WeatherActivity;
import top.maweihao.weather.contract.PreferenceConfigContact;
import top.maweihao.weather.entity.ForecastBean;
import top.maweihao.weather.entity.NewWeather;
import top.maweihao.weather.service.WidgetSyncService;
import top.maweihao.weather.util.LunarUtil;
import top.maweihao.weather.util.Utility;
import top.maweihao.weather.widget.BigWeatherWidget;
import top.maweihao.weather.widget.BigWidgetConfigureActivity;

/**
 * Util class for BigWeatherWidget
 * Created by ma on 17-7-27.
 */

public class BigWidgetUtils {

    private static final String TAG = BigWidgetUtils.class.getSimpleName();
    private static final int WEATHER_PENDING_INTENT_CODE = 123;
    private static final int CLOCK_PENDING_INTENT_CODE = 223;
    private static final int TALL_WIDGET_REFRESH_CODE = 323;

    public static boolean isEnable(Context context) {
        int[] widgetIds = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(new ComponentName(context, BigWeatherWidget.class));
        return widgetIds != null && widgetIds.length > 0;
    }

    public static void refreshWidgetView(Context context, NewWeather weather, String countyName) {
        RemoteViews bigViews = new RemoteViews(context.getPackageName(), R.layout.widget_big_weather);

        String description = weather.getResult().getMinutely().getDescription();
        int tem = Utility.intRoundDouble(weather.getResult().getHourly().getTemperature().get(0).getValue());
        String skycon = weather.getResult().getHourly().getSkycon().get(0).getValue();
        double intensity = weather.getResult().getHourly().getPrecipitation().get(0).getValue();
        String skyconString = Utility.chooseWeatherSkycon(context, skycon, intensity, WeatherActivity.HOURLY_MODE);
        int icon = Utility.chooseWeatherIcon(skycon, intensity, WeatherActivity.HOURLY_MODE, false);

        LunarUtil lunarUtilDate = new LunarUtil(new GregorianCalendar());
        bigViews.setTextViewText(R.id.big_widget_lunar, lunarUtilDate.toString());

        bigViews.setTextViewText(R.id.big_widget_description, description);
        bigViews.setImageViewResource(R.id.big_widget_skycon, icon);
        bigViews.setTextViewText(R.id.big_widget_info, countyName + "\n" + skyconString + ' ' + tem + '°');
        bigViews.setTextViewText(R.id.big_widget_refresh_time, Utility.parseTime(context));

        boolean lunar = BigWidgetConfigureActivity.loadLunarPref(context);
        boolean card = BigWidgetConfigureActivity.loadCardPref(context);

        if (card) {
            bigViews.setViewVisibility(R.id.widget_big_card, View.VISIBLE);
        } else {
            bigViews.setViewVisibility(R.id.widget_big_card, View.GONE);
        }

        if (lunar) {
            bigViews.setViewVisibility(R.id.big_widget_refresh_time, View.GONE);
            bigViews.setViewVisibility(R.id.iv_refresh, View.GONE);
            bigViews.setViewVisibility(R.id.big_widget_lunar, View.VISIBLE);
        } else {
            bigViews.setViewVisibility(R.id.big_widget_refresh_time, View.VISIBLE);
            bigViews.setViewVisibility(R.id.iv_refresh, View.VISIBLE);
            bigViews.setViewVisibility(R.id.big_widget_lunar, View.GONE);
        }

        PendingIntent weatherPendingIntent = PendingIntent.getActivity(context, WEATHER_PENDING_INTENT_CODE,
                new Intent(context, WeatherActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Intent mClockIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        mClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent clockPendingIntent = PendingIntent.getActivity(context, CLOCK_PENDING_INTENT_CODE,
                mClockIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intent = new Intent(context, WidgetSyncService.class);
        intent.putExtra(WidgetSyncService.force_refresh, true);
        PendingIntent refreshPendingIntent = PendingIntent.getService(context, TALL_WIDGET_REFRESH_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        bigViews.setOnClickPendingIntent(R.id.big_widget_clock, clockPendingIntent);
        bigViews.setOnClickPendingIntent(R.id.big_widget_skycon, weatherPendingIntent);
        bigViews.setOnClickPendingIntent(R.id.big_weather_refresh, refreshPendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        appWidgetManager.updateAppWidget(new ComponentName(context, BigWeatherWidget.class), bigViews);
    }

    @Deprecated
    public static void refreshWidgetView(Context context, ForecastBean weather) {

        PreferenceConfigContact configContact = Utility.createSimpleConfig(context).create(PreferenceConfigContact.class);
        String countyName = configContact.getCountyName() == null ? "error" : configContact.getCountyName();

        RemoteViews bigViews = new RemoteViews(context.getPackageName(), R.layout.widget_big_weather);

        String description = weather.getResult().getMinutely().getDescription();
        int tem = Utility.intRoundDouble(weather.getResult().getHourly().getTemperature().get(0).getValue());
        String skycon = weather.getResult().getHourly().getSkycon().get(0).getValue();
        double intensity = weather.getResult().getHourly().getPrecipitation().get(0).getValue();
        String skyconString = Utility.chooseWeatherSkycon(context, skycon, intensity, WeatherActivity.HOURLY_MODE);
        int icon = Utility.chooseWeatherIcon(skycon, intensity, WeatherActivity.HOURLY_MODE, false);

        LunarUtil lunarUtilDate = new LunarUtil(new GregorianCalendar());
        bigViews.setTextViewText(R.id.big_widget_lunar, lunarUtilDate.toString());

        bigViews.setTextViewText(R.id.big_widget_description, description);
        bigViews.setImageViewResource(R.id.big_widget_skycon, icon);
        bigViews.setTextViewText(R.id.big_widget_info, countyName + "\n" + skyconString + ' ' + tem + '°');
        bigViews.setTextViewText(R.id.big_widget_refresh_time, Utility.parseTime(context));

        PendingIntent weatherPendingIntent = PendingIntent.getActivity(context, WEATHER_PENDING_INTENT_CODE,
                new Intent(context, WeatherActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Intent mClockIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        mClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent clockPendingIntent = PendingIntent.getActivity(context, CLOCK_PENDING_INTENT_CODE,
                mClockIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intent = new Intent(context, WidgetSyncService.class);
        intent.putExtra(WidgetSyncService.force_refresh, true);
        PendingIntent refreshPendingIntent = PendingIntent.getService(context, TALL_WIDGET_REFRESH_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        bigViews.setOnClickPendingIntent(R.id.big_widget_clock, clockPendingIntent);
        bigViews.setOnClickPendingIntent(R.id.big_widget_skycon, weatherPendingIntent);
        bigViews.setOnClickPendingIntent(R.id.big_weather_refresh, refreshPendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        appWidgetManager.updateAppWidget(new ComponentName(context, BigWeatherWidget.class), bigViews);
    }

    @Deprecated
    public static void refreshWidgetView(Context context, ForecastBean forecastBean, int minute) {

        PreferenceConfigContact configContact = Utility.createSimpleConfig(context).create(PreferenceConfigContact.class);
        String countyName = configContact.getCountyName() == null ? "error" : configContact.getCountyName();

        RemoteViews bigViews = new RemoteViews(context.getPackageName(), R.layout.widget_big_weather);

        String description = forecastBean.getResult().getMinutely().getDescription();
        int tem = Utility.intRoundDouble(forecastBean.getResult().getHourly().getTemperature().get(0).getValue());
        String skycon = forecastBean.getResult().getHourly().getSkycon().get(0).getValue();
        double intensity = forecastBean.getResult().getHourly().getPrecipitation().get(0).getValue();
        String skyconString = Utility.chooseWeatherSkycon(context, skycon, intensity, WeatherActivity.HOURLY_MODE);
        int icon = Utility.chooseWeatherIcon(skycon, intensity, WeatherActivity.HOURLY_MODE, false);

        bigViews.setTextViewText(R.id.big_widget_description, description);
        bigViews.setImageViewResource(R.id.big_widget_skycon, icon);
        bigViews.setTextViewText(R.id.big_widget_info, countyName + "\n" + skyconString + ' ' + tem + '°');
        bigViews.setTextViewText(R.id.big_widget_refresh_time,
                Utility.parseTime(context) + '-' + Utility.parseTime(minute));

        PendingIntent weatherPendingIntent = PendingIntent.getActivity(context, WEATHER_PENDING_INTENT_CODE,
                new Intent(context, WeatherActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        Intent mClockIntent = new Intent(AlarmClock.ACTION_SHOW_ALARMS);
        mClockIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent clockPendingIntent = PendingIntent.getActivity(context, CLOCK_PENDING_INTENT_CODE,
                mClockIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intent = new Intent(context, WidgetSyncService.class);
        intent.putExtra(WidgetSyncService.force_refresh, true);
        PendingIntent refreshPendingIntent = PendingIntent.getService(context, TALL_WIDGET_REFRESH_CODE,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);

        bigViews.setOnClickPendingIntent(R.id.big_widget_clock, clockPendingIntent);
        bigViews.setOnClickPendingIntent(R.id.big_widget_skycon, weatherPendingIntent);
        bigViews.setOnClickPendingIntent(R.id.big_weather_refresh, refreshPendingIntent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        appWidgetManager.updateAppWidget(new ComponentName(context, BigWeatherWidget.class), bigViews);
    }

    @Deprecated
    public static void refreshTime(Context context, int minute) {
        RemoteViews bigViews = new RemoteViews(context.getPackageName(), R.layout.widget_big_weather);
        bigViews.setTextViewText(R.id.big_widget_refresh_time, '-' + Utility.parseTime(minute));
    }

}
