package top.maweihao.weather.model;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.text.TextUtils;
import android.util.Log;

import com.alibaba.fastjson.JSON;
import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import top.maweihao.weather.bean.BaiDu.BaiDuChoosePositionBean;
import top.maweihao.weather.bean.BaiDu.BaiDuCoordinateBean;
import top.maweihao.weather.bean.BaiDu.BaiDuIPLocationBean;
import top.maweihao.weather.bean.ForecastBean;
import top.maweihao.weather.bean.MyLocation;
import top.maweihao.weather.bean.RealTimeBean;
import top.maweihao.weather.contract.WeatherActivityContract;
import top.maweihao.weather.util.Constants;
import top.maweihao.weather.util.HttpUtil;
import top.maweihao.weather.util.Utility;

import static top.maweihao.weather.util.Constants.DEBUG;
import static top.maweihao.weather.util.Constants.SYSTEM_NOW_TIME;
import static top.maweihao.weather.util.Constants.Through.THROUGH_CHOOSE_POSITION;
import static top.maweihao.weather.util.Constants.Through.THROUGH_COORDINATE;
import static top.maweihao.weather.util.Constants.Through.THROUGH_IP;
import static top.maweihao.weather.util.Constants.Through.THROUGH_LOCATE;

/**
 * WeatherActivity 的 model 层
 * Created by limuyang on 2017/5/31.
 */


public class WeatherActivityModel implements WeatherActivityContract.Model {
    private static final String TAG = "WeatherActivityModel";
    //    private Boolean autoLocate;
    private String countyName = null;

    private String locationCoordinates;

    private Long locateTime;
    private LocationClient mLocationClient;
    private WeatherActivityContract.Presenter presenter;
    private Context context;
    private static ExecutorService singleThreadPool = Executors.newSingleThreadExecutor();

    public WeatherActivityModel(Context context, WeatherActivityContract.Presenter presenter) {
        this.context = context;
        this.presenter = presenter;

        mLocationClient = new LocationClient(context);
        mLocationClient.registerLocationListener(new MainLocationListener());
        initLocation();
    }

    /**
     * 刷新天气
     *
     * @param forceAllRefresh 是否强制全量刷新
     * @param getCountyName   需要刷新的城市名
     */
    @Override
    public void refreshWeather(boolean forceAllRefresh, @Nullable String getCountyName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        //读取配置
        boolean getAutoLocate = prefs.getBoolean("auto_locate", true);
        countyName = prefs.getString("countyName", null);
        if (!forceAllRefresh) {

            /*
             * 判断是否超过刷新间隔。
             * 如果需要刷新的城市和配置文件中的城市一样 或 传递进来的城市为空，则进行刷新间隔判断。
             */
            if ((getCountyName != null && countyName != null && countyName.equals(getCountyName)) || getCountyName == null) {

                 /*minInterval： 最低刷新间隔*/
                int minInterval = prefs.getInt("refresh_interval", 10);
//               现在的天气， 原始json
                String weatherNow = prefs.getString("weather_now", null);
                long weatherNowLastUpdateTime = prefs.getLong("weather_now_last_update_time", 0);
//               未来的天气， 原始json
                String weatherFull = prefs.getString("weather_full", null);
                long weatherFullLastUpdateTime = prefs.getLong("weather_full_last_update_time", 0);
//               若保存的天气刷新时间和现在相差小于 minInterval，则直接使用
                if (weatherNow != null && System.currentTimeMillis() - weatherNowLastUpdateTime < minInterval * 60 * 1000
                        && weatherFull != null && System.currentTimeMillis() - weatherFullLastUpdateTime < minInterval * 60 * 1000) {
                    if (DEBUG) {
                        Log.d(TAG, "readCache: last nowWeather synced: "
                                + (System.currentTimeMillis() - weatherNowLastUpdateTime) / 1000 + "s ago");
                        Log.d(TAG, "readCache: last fullWeather synced: "
                                + (System.currentTimeMillis() - weatherFullLastUpdateTime) / 1000 + "s ago");
                    }
//            lastUpdateTime.setText(Utility.getTime(context, weatherNowLastUpdateTime));
                    presenter.setLastUpdateTime(weatherNowLastUpdateTime);

                    RealTimeBean bean = JSON.parseObject(weatherNow, RealTimeBean.class);
                    presenter.setCurrentWeatherInfo(bean);
                    jsonFullWeatherData(weatherFull);

                    presenter.setLocateModeImage(getAutoLocate);

                    String ip;
                    if (countyName != null)
                        presenter.setCounty(countyName);
                    else if ((ip = prefs.getString("IP", null)) != null) {
                        presenter.setCounty(ip);
                    }
                } else {
//            全量刷新
                    beforeRequestWeather(getAutoLocate ? THROUGH_LOCATE : THROUGH_CHOOSE_POSITION);
                }
            } else {
                beforeRequestWeather(getAutoLocate ? THROUGH_LOCATE : THROUGH_CHOOSE_POSITION);
            }
        } else {
//            全量刷新
            if (getCountyName != null)
                countyName = getCountyName;
            beforeRequestWeather(getAutoLocate ? THROUGH_LOCATE : THROUGH_CHOOSE_POSITION);
        }
    }

    @Override
    public void beforeRequestWeather(@Constants.Through int requestCode) {
        presenter.startSwipe();
//        locateMode.setVisibility(View.VISIBLE);
        switch (requestCode) {
            case THROUGH_IP:
//                主界面显示当前为 ip 定位
//                locateMode 和 locateModeImage 用来显示当前定位方式
                presenter.setLocateModeImage(true);
                presenter.setCounty(Utility.getIP(context));
                getCoordinateByIp();
                break;
            case THROUGH_CHOOSE_POSITION:
                presenter.setLocateModeImage(false);
                getCoordinateByChoosePosition(countyName);
                break;
            case THROUGH_LOCATE:
                presenter.setLocateModeImage(true);
                bdLocate();
                break;
            case THROUGH_COORDINATE:
//                locateModeImage.setVisibility(View.INVISIBLE);
//                locateMode.setVisibility(View.INVISIBLE);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                locationCoordinates = prefs.getString("coordinate", null);
//                initRequireUrl();
                afterGetCoordinate();
                break;
        }
    }

    /**
     * 初始化百度定位
     */
    private void initLocation() {
        LocationClientOption option = new LocationClientOption();
//        刷新间隔（ms）
        option.setScanSpan(1000);
//        定位模式：精确
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        // 需要地址信息
        option.setIsNeedAddress(true);
        mLocationClient.setLocOption(option);
    }

    /**
     * 使用百度定位
     */
    private void bdLocate() {
        locateTime = System.currentTimeMillis();
        //等着回调 MainLocationListener
        mLocationClient.start();
    }

    @Override
    public void stopBdLocation() {
        if (mLocationClient.isStarted()) {
            mLocationClient.stop();
        }
    }

    /**
     * 百度定位成功
     *
     * @param location 自定义的位置类
     */
    private void bdLocateSuccess(MyLocation location) {
        locationCoordinates = location.getCoordinate();
        String countyName = location.getDistrict();
        if (DEBUG) {
            Log.d(TAG, "locateSuccess: locationCoordinates == " + locationCoordinates);
            Log.d(TAG, "locateSuccess: location: " + countyName + location.getStreet());
        }
        SharedPreferences.Editor editor = PreferenceManager
                .getDefaultSharedPreferences(context).edit();
        editor.putString("coordinate", locationCoordinates);
        editor.putLong("coordinate_last_update", System.currentTimeMillis());
        editor.putString("countyName", countyName);
        editor.putLong("countyName_last_update_time", System.currentTimeMillis());
        editor.apply();
        presenter.setCounty(countyName);
        afterGetCoordinate();
    }

    /**
     * 当百度定位失败时，使用 locationManager 定位
     */
    private void bdLocateFail() {
        LocationManager mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        Location location = null;
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            location = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }

        if (location != null) {
            locationCoordinates = String.valueOf(location.getLongitude()) + ',' + String.valueOf(location.getLatitude());
            if (DEBUG)
                Log.d(TAG, "GetCoordinateByLocate: locationCoordinates = " + locationCoordinates);
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(context).edit();
            editor.putString("coordinate", locationCoordinates);
            editor.putLong("coordinate_last_update", System.currentTimeMillis());
            editor.apply();

            getCountyByCoordinate(locationCoordinates);
            afterGetCoordinate();
        } else {
            if (DEBUG)
                Log.d(TAG, "requestLocation: location == null, switch to IP method");
            beforeRequestWeather(THROUGH_IP);
        }
    }

    /**
     * 百度定位监听类
     */
    private class MainLocationListener implements BDLocationListener {
        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            Log.d(TAG, "BAIDU: received" + (System.currentTimeMillis() - locateTime));
            if (System.currentTimeMillis() - locateTime < 3 * 1000) {  //at most x second
                if (bdLocation.getLocType() != BDLocation.TypeGpsLocation) {
                    // do nothing
                    if (DEBUG) {
                        Log.d(TAG, "BAIDU onReceiveLocation: Locate type == " + bdLocation.getLocType());
                    }
                } else {
                    mLocationClient.stop();
                    if (DEBUG) {
                        Log.d(TAG, "BAIDU onReceiveLocation: Locate type == GPS");
                        presenter.toastMessage("GPS!");
                    }
                    bdLocateSuccess(simplifyBDLocation(bdLocation));
                }
            } else {
                mLocationClient.stop();
                if (DEBUG) {
                    Log.d(TAG, "BAIDU onReceiveLocation: baidu locate time out, locType == " + bdLocation.getLocType());
                }
                if (MyLocation.locateSuccess(bdLocation.getLocType())) {
                    bdLocateSuccess(simplifyBDLocation(bdLocation));
                } else {
                    Log.d(TAG, "BAIDU onReceiveLocation: baidu locate failed, switch to LocationManager method");
                    bdLocateFail();
                }
            }
        }

        @Override
        public void onConnectHotSpotMessage(String s, int i) {
//      nothing to do
        }

        private MyLocation simplifyBDLocation(BDLocation bdLocation) {
            MyLocation location = new MyLocation(bdLocation.getLongitude(), bdLocation.getLatitude());
            location.setType(bdLocation.getLocType());
            location.setProvince(bdLocation.getProvince());
            location.setCity(bdLocation.getCity());
            location.setDistrict(bdLocation.getDistrict());
            location.setStreet(bdLocation.getStreet());
            return location;
        }
    }
    //=========================================================================================================

    /**
     * 网络请求未来天气数据
     *
     * @param url 网址
     */
//    @Override
    private void requestFullWeather(String url) {
        if (TextUtils.isEmpty(url)) {
            if (DEBUG)
                Log.d(TAG, "requestFullWeather: url = null");
            return;
        }
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                presenter.toastMessage("load full weather failed");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseText = response.body().string();
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(context).edit();
                editor.putString("weather_full", responseText);
                editor.putLong("weather_full_last_update_time", System.currentTimeMillis());

                editor.apply();

//                presenter.isUpdate(true);
                presenter.setLastUpdateTime(SYSTEM_NOW_TIME);
                jsonFullWeatherData(responseText);
            }
        });
    }


    /**
     * 网络请求现在的天气
     */
//    @Override
    private void requestCurrentWeather(String url) {

        if (TextUtils.isEmpty(url)) {
            if (DEBUG)
                Log.d(TAG, "requestCurrentWeather: url = null");
            return;
        }
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();

                presenter.toastMessage("load current weather failed");
                presenter.stopSwipe();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseText = response.body().string();
//                final WeatherData weatherData = handleCurrentWeatherResponse(responseText);
                RealTimeBean bean = JSON.parseObject(responseText, RealTimeBean.class);

                if (bean != null) {
                    SharedPreferences.Editor editor = PreferenceManager
                            .getDefaultSharedPreferences(context).edit();
                    editor.putString("weather_now", responseText);
                    editor.putLong("weather_now_last_update_time", System.currentTimeMillis());

                    editor.apply();
                    presenter.setCurrentWeatherInfo(bean);
                    presenter.setLastUpdateTime(SYSTEM_NOW_TIME);
                } else {
                    presenter.toastMessage("weatherDate = null");
                }
            }
        });
    }

    /**
     * 解析获得的未来天气json
     *
     * @param responseText json
     */
    @SuppressWarnings("unchecked")
    private void jsonFullWeatherData(String responseText) {
        //fastJson解析数据
        ForecastBean forecastBean = JSON.parseObject(responseText, ForecastBean.class);
        if (forecastBean.getStatus().equals(Constants.STATUS_OK)) {
            List list;
            //返回的json是48小时的预报，截取前24小时
            if ((list = forecastBean.getResult().getHourly().getSkycon()) != null)
                forecastBean.getResult().getHourly().setSkycon(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getCloudrate()) != null)
                forecastBean.getResult().getHourly().setCloudrate(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getAqi()) != null)
                forecastBean.getResult().getHourly().setAqi(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getHumidity()) != null)
                forecastBean.getResult().getHourly().setHumidity(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getPm25()) != null)
                forecastBean.getResult().getHourly().setPm25(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getPrecipitation()) != null)
                forecastBean.getResult().getHourly().setPrecipitation(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getWind()) != null)
                forecastBean.getResult().getHourly().setWind(list.subList(0, 23));
            if ((list = forecastBean.getResult().getHourly().getTemperature()) != null)
                forecastBean.getResult().getHourly().setTemperature(list.subList(0, 23));
            //截取前5天
            if ((list = forecastBean.getResult().getDaily().getColdRisk()) != null)
                forecastBean.getResult().getDaily().setColdRisk(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getTemperature()) != null)
                forecastBean.getResult().getDaily().setTemperature(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getSkycon()) != null)
                forecastBean.getResult().getDaily().setSkycon(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getCloudrate()) != null)
                forecastBean.getResult().getDaily().setCloudrate(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getAqi()) != null)
                forecastBean.getResult().getDaily().setAqi(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getHumidity()) != null)
                forecastBean.getResult().getDaily().setHumidity(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getAstro()) != null)
                forecastBean.getResult().getDaily().setAstro(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getUltraviolet()) != null)
                forecastBean.getResult().getDaily().setUltraviolet(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getPm25()) != null)
                forecastBean.getResult().getDaily().setPm25(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getDressing()) != null)
                forecastBean.getResult().getDaily().setDressing(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getCarWashing()) != null)
                forecastBean.getResult().getDaily().setCarWashing(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getPrecipitation()) != null)
                forecastBean.getResult().getDaily().setPrecipitation(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getWind()) != null)
                forecastBean.getResult().getDaily().setWind(list.subList(0, 5));
            if ((list = forecastBean.getResult().getDaily().getDesc()) != null)
                forecastBean.getResult().getDaily().setDesc(list.subList(0, 5));


            ForecastBean.ResultBean.HourlyBean hourlyBean = forecastBean.getResult().getHourly();
            ForecastBean.ResultBean.DailyBean dailyBean = forecastBean.getResult().getDaily();

            presenter.setDailyWeatherInfo(dailyBean);
            presenter.setHourlyWeatherInfo(hourlyBean);
            presenter.rainInfo(forecastBean.getResult().getMinutely().getDescription());
        }
    }

    //=========================================================================================================


    /**
     * 通过获取的坐标获得位置描述， 使用 baidu web api
     *
     * @param coordinate 坐标
     */
    private void getCountyByCoordinate(String coordinate) {
        String url;
        if (!TextUtils.isEmpty(coordinate)) {
            String[] part = coordinate.split(",");
            String reverseCoordinate = part[1] + ',' + part[0];
            url = "http://api.map.baidu.com/geocoder/v2/?location=" + reverseCoordinate + "&output=json&pois=1&ak=eTTiuvV4YisaBbLwvj4p8drl7BGfl1eo";
        } else {
            if (DEBUG)
                Log.e(TAG, "WeatherActivity::setCountyByCoordinate: coordinate == null");
            return;
        }
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseText = response.body().string();
                BaiDuCoordinateBean bean = JSON.parseObject(responseText, BaiDuCoordinateBean.class);
                String countyName = bean.getResult().getAddressComponent().getDistrict();
                if (DEBUG)
                    Log.d(TAG, "setCountyByCoordinate.onResponse: countyName: " + countyName);
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(context).edit();
                editor.putString("countyName", countyName);
                editor.putLong("countyName_last_update_time", System.currentTimeMillis());
                editor.apply();
                presenter.setCounty(countyName);

            }
        });
    }


    /**
     * 选择地址进行定位
     */
    private void getCoordinateByChoosePosition(String countyName) {
//        if (TextUtils.isEmpty(countyName)) { //若无保存的地址，则打开 ChoosePositionActivity
//            Log.e(TAG, "GetCoordinateByChoosePosition: choosed countyName == null");
//            Toast.makeText(WeatherActivity.this, getResources().getString(R.string.choose_your_position),
//                    Toast.LENGTH_LONG).show();
//            Intent intent = new Intent(WeatherActivity.this, ChoosePositionActivity.class);
//            startActivityForResult(intent, 1);
//        } else {
//            final Message message = handler.obtainMessage();
//            message.what = HANDLE_POSITION;
//            message.obj = countyName;
//            handler.sendMessage(message);

        String url = "http://api.map.baidu.com/geocoder/v2/?output=json&address=%" + countyName + "&ak=eTTiuvV4YisaBbLwvj4p8drl7BGfl1eo";
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                if (DEBUG)
                    Log.e(TAG, "GetCoordinateByChoosePosition: failed");
                presenter.stopSwipe();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                BaiDuChoosePositionBean bean = JSON.parseObject(responseText, BaiDuChoosePositionBean.class);

                locationCoordinates = bean.getResult().getLocation().getLng() + "," + bean.getResult().getLocation().getLat();
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(context).edit();
                editor.putString("coordinate", locationCoordinates);
                editor.putLong("coordinate_last_update", System.currentTimeMillis());
                editor.apply();
                afterGetCoordinate();
            }
        });
//        }
    }

    /**
     * 当位置请求失败时，通过 ip 地址判别位置
     */
    private void getCoordinateByIp() {
//        百度 web api
        String url = "http://api.map.baidu.com/location/ip?ak=eTTiuvV4YisaBbLwvj4p8drl7BGfl1eo&coor=bd09ll";
        HttpUtil.sendOkHttpRequest(url, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (DEBUG)
                    Log.e(TAG, "onFailure: fetch locationCoordinates by IP failed");
                presenter.toastMessage("onFailure: fetch locationCoordinates by IP failed");
                presenter.stopSwipe();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                final String responseText = response.body().string();
                BaiDuIPLocationBean bean = JSON.parseObject(responseText, BaiDuIPLocationBean.class);
                locationCoordinates = bean.getContent().getPoint().getX() + "," + bean.getContent().getPoint().getY();
                String countyName = bean.getContent().getAddress_detail().getCity() + " " + bean.getContent().getAddress_detail().getDistrict();
//                try {
//                    JSONObject allAttributes = new JSONObject(responseText);
//                    JSONObject content = allAttributes.getJSONObject("content");
//                    JSONObject address_detail = content.getJSONObject("address_detail");
//                    String countyName = address_detail.getString("city") + " " + address_detail.getString("district");
//                    JSONObject point = content.getJSONObject("point");
//                    String x = point.getString("x");
//                    String y = point.getString("y");
//                    locationCoordinates = x + ',' + y;
                SharedPreferences.Editor editor = PreferenceManager
                        .getDefaultSharedPreferences(context).edit();
                editor.putString("coordinate", locationCoordinates);
                editor.putLong("coordinate_last_update", System.currentTimeMillis());
                editor.putString("countyName", countyName);
                editor.putLong("countyName_last_update_time", System.currentTimeMillis());
                editor.putString("IP", Utility.getIP(context));
                editor.apply();
                if (DEBUG)
                    Log.d(TAG, "GetCoordinateByIp: locationCoordinates = " + locationCoordinates);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                    if (DEBUG) {
//                        Log.e(TAG, "GetCoordinateByIp: parse IP address json error");
//                        Log.d(TAG, "response: " + responseText);
//                    }
//                }
                afterGetCoordinate();
            }
        });
    }

    /**
     * 初始化请求天气的url
     */
    private void initRequireUrl() {
        if (!TextUtils.isEmpty(locationCoordinates)) {
//            即 current weather Url， 获取当前天气的url
            String cUrl = "https://api.caiyunapp.com/v2/3a9KGv6UhM=btTHY/" + locationCoordinates + "/realtime.json";
//            即 full weather Url， 获取未来天气的url
            String fUrl = "https://api.caiyunapp.com/v2/3a9KGv6UhM=btTHY/" + locationCoordinates + "/forecast.json";
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(context).edit();
            editor.putString("curl", cUrl);
            editor.putString("furl", fUrl);
            editor.apply();

            presenter.startSwipe();

            requestCurrentWeather(cUrl);
            requestFullWeather(fUrl);
        } else {
            presenter.toastMessage("locationCoordinates = null");
            if (DEBUG)
                Log.e(TAG, "initRequireUrl: locationCoordinates = null");
        }
    }


    private void afterGetCoordinate() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                initRequireUrl();
            }
        };
        if (singleThreadPool == null) {
            singleThreadPool = Executors.newSingleThreadExecutor();
        }
        singleThreadPool.execute(runnable);
    }

    @Override
    public void destroy() {
        context = null;
//        singleThreadPool.shutdownNow();
    }
}
