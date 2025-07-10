package com.zyc.zcontrol.deviceItem.tc1;


import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.zyc.webservice.WebService;
import com.zyc.zcontrol.R;
import com.zyc.zcontrol.deviceItem.DeviceClass.DeviceTC1;
import com.zyc.zcontrol.deviceItem.DeviceClass.SettingFragment;

import org.json.JSONException;
import org.json.JSONObject;

@SuppressLint("ValidFragment")
public class TC1SettingFragment extends SettingFragment {
    final static String Tag = "TC1SettingFragment";

    Preference ssid;
    Preference fw_version;
    Preference lock;
    Preference restart;
    Preference regetdata;
    EditTextPreference name_preference;
    EditTextPreference interval;
    EditTextPreference power_calibration;
    CheckBoxPreference old_protocol;
    SwitchPreference child_lock;
    SwitchPreference led_lock;
    DeviceTC1 device;

    boolean ota_flag = false;
    private ProgressDialog pd;

    private TC1OTAInfo otaInfo = new TC1OTAInfo();

    //region Handler
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {// handler接收到消息后就会执行此方法
            JSONObject obj = null;
            String JsonStr = null;
            switch (msg.what) {
                //region 获取最新版本信息
                case 0:
                    if (pd != null && pd.isShowing()) pd.dismiss();
                    JsonStr = (String) msg.obj;
                    Log.d(Tag, "result:" + JsonStr);
                    try {
                        if (JsonStr == null || JsonStr.length() < 3)
                            throw new JSONException("获取最新版本信息失败");
                        obj = new JSONObject(JsonStr);
                        if (obj.has("id") && obj.has("tag_name") && obj.has("target_commitish")
                                && obj.has("name") && obj.has("body") && obj.has("created_at")
                                && obj.has("assets")) {
                            otaInfo.title = obj.getString("name");   //
                            otaInfo.message = obj.getString("body");
                            otaInfo.tag_name = obj.getString("tag_name");
                            otaInfo.created_at = obj.getString("created_at");

                            String version = fw_version.getSummary().toString();
                            if (!version.equals(otaInfo.tag_name)) {
                                handler.sendEmptyMessage(1);
                            } else {
                                Toast.makeText(getActivity(), "已是最新版本", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            throw new JSONException("获取最新版本信息失败");
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), "获取最新版本信息失败", Toast.LENGTH_SHORT).show();
                    }

                    break;
                //endregion
                //region 开始获取固件下载地址
                case 1:
                    pd.setMessage("正在获取固件地址,请稍后....");
                    pd.setCanceledOnTouchOutside(false);
                    pd.show();

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Message msg = new Message();
                            msg.what = 2;
                            String res = WebService.WebConnect("https://gitee.com/api/v5/repos/a2633063/Release/releases/tags/zTC1");
                            msg.obj = res;
                            handler.sendMessageDelayed(msg, 0);// 执行耗时的方法之后发送消给handler
                        }
                    }).start();
                    break;
                //endregion
                //region 已获取固件下载地址
                case 2:
                    if (pd != null && pd.isShowing()) pd.dismiss();
                    JsonStr = (String) msg.obj;
                    Log.d(Tag, "result:" + JsonStr);
                    try {
                        if (JsonStr == null || JsonStr.length() < 3)
                            throw new JSONException("获取固件下载地址失败");

                        obj = new JSONObject(JsonStr);

                        if (obj.getString("name").equals("zTC1发布地址_" + otaInfo.tag_name)) {
                            String otauriAll = obj.getString("body");
                            otaInfo.ota = otauriAll.trim();

                            AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                                    .setTitle("获取到最新版本:" + otaInfo.tag_name)
                                    .setMessage(otaInfo.title + "\n" + otaInfo.message)
                                    .setPositiveButton("更新", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            Send("{\"mac\":\"" + device.getMac() + "\",\"setting\":{\"ota\":\"" + otaInfo.ota + "\"}}");
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .create();
                            alertDialog.show();
                        } else
                            throw new JSONException("获取固件下载地址获取失败");

                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(getActivity(), "固件下载地址获取失败", Toast.LENGTH_SHORT).show();

                    }

                    break;
                //endregion
                //region 发送请求数据
                case 3:
                    handler.removeMessages(3);
                    Send("{\"mac\":\"" + device.getMac() + "\",\"version\":null,\"led_lock\":null,\"child_lock\":null,\"interval\":null,\"power_calibration\":null,\"lock\":null,\"ssid\":null}");
                    break;
                //endregion
            }
        }
    };
    //endregion

    public TC1SettingFragment(DeviceTC1 device) {
        super(device.getName(), device.getMac());
        this.device = device;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesName("Setting_" + device.getMac());

        Log.d(Tag, "设置文件:" + "Setting" + device.getMac());
        addPreferencesFromResource(R.xml.tc1_setting);

        ssid = findPreference("ssid");
        fw_version = findPreference("fw_version");
        lock = findPreference("lock");
        restart = findPreference("restart");
        regetdata = findPreference("regetdata");
        name_preference = (EditTextPreference) findPreference("name");
        interval = (EditTextPreference) findPreference("interval");
        power_calibration = (EditTextPreference) findPreference("power_calibration");
        old_protocol = (CheckBoxPreference) findPreference("old_protocol");
        child_lock = (SwitchPreference) findPreference("child_lock");
        led_lock = (SwitchPreference) findPreference("led_lock");

        name_preference.setSummary(device.getName());

        //region mac地址
        findPreference("mac").setSummary(device.getMac());
        findPreference("mac").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    ClipboardManager clipboardManager = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("text", device.getMac()));
                    Toast.makeText(getActivity(), "已复制mac地址", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "复制mac地址失败", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
                return false;
            }
        });
        //endregion

        //region 设置名称
        name_preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                Send("{\"mac\":\"" + device.getMac() + "\",\"setting\":{\"name\":\"" + (String) newValue + "\"}}");
                return false;
            }
        });
        //endregion
        //region 激活
        lock.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //region 未获取到当前激活信息
                if (lock.getSummary() == null) {
                    AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                            .setTitle("未获取到当前设备激活信息")
                            .setMessage("请获取到当前设备激活信息后重试.")
                            .setNegativeButton("确定", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
//                                    getActivity().finish();
                                }
                            })
                            .create();
                    alertDialog.show();
                    return false;
                }
                //endregion

                unlock();
                return false;
            }
        });
        //endregion
        //region 设置反馈间隔
        interval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                int val = Integer.parseInt((String) newValue);

                if (val > 0 && val <= 255) {
                    Send("{\"mac\":\"" + device.getMac() + "\",\"interval\":" + (String) newValue + "}");
                } else {
                    Toast.makeText(getActivity(), "输入有误!范围1-255", Toast.LENGTH_SHORT).show();
                }
                return false;
            }
        });
        //endregion
        //region 设置功率校准系数
        power_calibration.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {

                //int val = Integer.parseInt((String) newValue);
                //if (val > 0) {
                Send("{\"mac\":\"" + device.getMac() + "\",\"power_calibration\":" + (String) newValue + "}");
                //} else {
                //   Toast.makeText(getActivity(), "输入有误!范围1-255", Toast.LENGTH_SHORT).show();
                //}
                return false;
            }
        });
        //endregion

        //region 夜间模式 led锁
        led_lock.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                led_lock.setChecked(!led_lock.isChecked());
                if (!led_lock.isChecked()) {
                    Send("{\"mac\":\"" + device.getMac() + "\",\"led_lock\":1}");
                } else {
                    Send("{\"mac\":\"" + device.getMac() + "\",\"led_lock\":0}");
                }
                return true;
            }
        });
        //endregion

        //region 童锁
        child_lock.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                child_lock.setChecked(!child_lock.isChecked());
                if (!child_lock.isChecked()) {
                    Send("{\"mac\":\"" + device.getMac() + "\",\"child_lock\":1}");
                } else {
                    Send("{\"mac\":\"" + device.getMac() + "\",\"child_lock\":0}");
                }
                return true;
            }
        });
        //endregion
        //region 版本
        fw_version.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                //region 手动输入固件下载地址注释

//                final EditText et = new EditText(getActivity());
//                new AlertDialog.Builder(getActivity()).setTitle("请输入固件下载地址")
//                        .setView(et)
//                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialogInterface, int i) {
//                                String uri = et.getText().toString();
//                                if (uri.length() < 1) return;
//                                if (uri.startsWith("http")) {
//                                    Send("{\"mac\":\"" + device.getMac() + "\",\"setting\":{\"ota\":\"" + uri + "\"}}");
//                                }
//                            }
//                        }).setNegativeButton("取消", null).show();

                //endregion

                //未获取到当前版本信息
                if (!isGetVersion()) return false;

                String version = fw_version.getSummary().toString();
                //region 获取最新版本
                pd = new ProgressDialog(getActivity());
                pd.setMessage("正在获取最新固件版本,请稍后....");
                pd.setCanceledOnTouchOutside(false);
                pd.show();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Message msg = new Message();
                        msg.what = 0;
                        String res = WebService.WebConnect("https://gitee.com/api/v5/repos/a2633063/zTC1/releases/latest");
                        msg.obj = res;

                        handler.sendMessageDelayed(msg, 0);// 执行耗时的方法之后发送消给handler
                    }
                }).start();


                //endregion
                return false;
            }
        });
        //endregion
        //region 重启设备
        restart.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {

                new AlertDialog.Builder(getActivity()).setTitle("重启设备?")
                        .setMessage("如果设备死机此重启可能无效,依然需要手动拔插插头才能重启设备")
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Send("{\"mac\":\"" + device.getMac() + "\",\"cmd\":\"restart\"}");

                            }
                        }).setNegativeButton("取消", null).show();

                //endregion

                return false;
            }
        });
        //endregion
        //region 重新获取数据
        regetdata.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                handler.sendEmptyMessage(3);
                return false;
            }
        });
        //endregion

    }


    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(Tag, "longclick:" + position);
        if (position == fw_version.getOrder() + 1) {
            debugFWUpdate();
            return true;
        }
        return false;
    }

    //region 弹窗
    //region 判断是否获取当前版本号
    boolean isGetVersion() {
        //region 未获取到当前版本信息
//        if (device.getVersion() == null) {
        if (fw_version.getSummary() == null) {
            AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setTitle("未获取到当前设备版本")
                    .setMessage("请点击重新获取数据.获取到当前设备版本后重试.")
                    .setNegativeButton("确定", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            handler.sendEmptyMessageDelayed(3, 0);
                            Toast.makeText(getActivity(), "请求版本数据...", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .create();
            alertDialog.show();
            return false;
        } else return true;
        //endregion
    }
    //endregion

    //region 手动输入固件下载地址
    void debugFWUpdate() {
        //未获取到当前版本信息
        if (!isGetVersion()) return;
        final EditText et = new EditText(getActivity());
        new AlertDialog.Builder(getActivity()).setTitle("请输入固件下载地址")
                .setMessage("警告:输入错误的地址可能导致固件损坏!")
                .setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String uri = et.getText().toString();
                        if (uri.length() < 1) return;
                        if (uri.startsWith("http")) {
                            Send("{\"mac\":\"" + device.getMac() + "\",\"setting\":{\"ota\":\"" + uri + "\"}}");
                        }
                    }
                }).setNegativeButton("取消", null).show();
    }
    //endregion

    //region 弹窗激活
    void unlock() {

        final EditText et = new EditText(getActivity());
        AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                .setTitle("请输入激活码")
                .setView(et)
                .setMessage("索要激活码请至项目主页中查看作者联系方式.(关于页面中有项目跳转按钮)")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String lockStr = et.getText().toString();
                        lockStr = lockStr.replace("\r\n", "\n").replace("\n", "").replace(" ", "").trim();

                        if (lockStr.length() != 32) {
                            new AlertDialog.Builder(getActivity()).setTitle("注意:")
                                    .setMessage("激活码长度错误,请确认激活码格式!")
                                    .setPositiveButton("确定", null).show();
                            return;
                        }
                        Send("{\"mac\":\"" + device.getMac() + "\",\"lock\":\"" + lockStr + "\"}");
                    }
                })
                .setNegativeButton("取消", null).create();
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "激活帮助", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Uri uri = Uri.parse("https://github.com/a2633063/SmartControl_Android_MQTT/wiki/%E6%BF%80%E6%B4%BB%E7%A0%81%E8%8E%B7%E5%8F%96");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        });
        alertDialog.show();

    }

    //endregion
    //endregion

    public void Send(String message) {
        boolean udp = getActivity().getSharedPreferences("Setting_" + device.getMac(), 0).getBoolean("always_UDP", false);
        boolean oldProtocol = getActivity().getSharedPreferences("Setting_" + device.getMac(), 0).getBoolean("old_protocol", false);

        String topic = null;
        if (!udp) {
            if (oldProtocol) topic = "device/ztc1/set";
            else topic = device.getSendMqttTopic();
        }
        super.Send(udp, topic, message);
    }

    @Override
    public void Receive(String ip, int port, String topic, String message) {
        super.Receive(ip, port, topic, message);
        Log.d(Tag, "RECV DATA,topic:" + topic + ",content:" + message);

        try {
            JSONObject jsonObject = new JSONObject(message);

            if (!jsonObject.has("mac") || !jsonObject.getString("mac").equals(device.getMac())) {
                return;
            }


            //region 获取名称
            if (jsonObject.has("name")) {
                device.setName(jsonObject.getString("name"));
                name_preference.setSummary(device.getName());
                name_preference.setText(device.getName());
            }
            //endregion
            //region ssid
            if (jsonObject.has("ssid")) {
                ssid.setSummary(jsonObject.getString("ssid"));
            }
            //endregion
            //region 获取版本号
            if (jsonObject.has("version")) {
                fw_version.setSummary(jsonObject.getString("version"));
                if (jsonObject.getString("version").startsWith("v0.") && !old_protocol.isChecked()) {
                    Toast.makeText(getActivity(), "版本低于v1.0.0请勾选使用旧版通信协议!", Toast.LENGTH_LONG).show();
                }
            }
            //endregion
            //region 获取间隔时间
            if (jsonObject.has("interval")) {
                int interval_time = jsonObject.getInt("interval");
                interval.setSummary(String.valueOf(interval_time));
                interval.setText(String.valueOf(interval_time));
            }
            //endregion
            //region 夜间模式 led锁
            if (jsonObject.has("led_lock")) {
                int led_lock_val = jsonObject.getInt("led_lock");
                led_lock.setChecked(led_lock_val != 0);
            }
            //endregion
            //region 童锁
            if (jsonObject.has("child_lock")) {
                int child_lock_val = jsonObject.getInt("child_lock");
                child_lock.setChecked(child_lock_val != 0);
            }
            //endregion
            //region 激活
            if (jsonObject.has("lock")) {
//                device.setLock(jsonObject.getBoolean("lock"));
//                if (device.isLock()) {
//                    lock.setSummary("已激活");
//                } else {
//                    lock.setSummary("未激活");
//                    Toast.makeText(getActivity(), "未激活", Toast.LENGTH_SHORT).show();
//                }
                if (jsonObject.getBoolean("lock")) {
                    lock.setSummary("已激活");
                } else {
                    lock.setSummary("未激活");
                    Toast.makeText(getActivity(), "未激活", Toast.LENGTH_SHORT).show();
                }
            }
            //endregion
            //region ota结果/进度
            if (jsonObject.has("ota_progress")) {
                int ota_progress = jsonObject.getInt("ota_progress");

                if (!(ota_progress >= 0 && ota_progress < 100) && pd != null && pd.isShowing()) {
                    pd.dismiss();

                    String m = "固件更新成功!";
                    if (ota_progress == -1) {
                        m = "固件更新失败!请重试";
                    }
                    if (ota_flag) {
                        ota_flag = false;
                        new android.app.AlertDialog.Builder(getActivity())
                                .setTitle("")
                                .setMessage(m)
                                .setPositiveButton("确定", null)
                                .show();
                    }
                } else {
                    if (ota_flag) {
                        //todo 显示更新进度

                        if (pd != null && pd.isShowing())
                            pd.setMessage("正在获取最新固件版本,请稍后....\n可以直接取消此窗口,不影响设备ota过程\n" + "进度:" + ota_progress + "%");
//                        Toast.makeText(getActivity(), "ota进度:"+ota_progress+"%", Toast.LENGTH_SHORT).show();
                    }
                }

            }
            //endregion
            //region 接收主机setting
            JSONObject jsonSetting = null;
            if (jsonObject.has("setting")) jsonSetting = jsonObject.getJSONObject("setting");
            if (jsonSetting != null) {
                //region ota
                if (jsonSetting.has("ota")) {
                    String ota_uri = jsonSetting.getString("ota");
                    if (ota_uri.endsWith("ota.bin")) {
                        ota_flag = true;
                        pd = new ProgressDialog(getActivity());
                        pd.setButton(DialogInterface.BUTTON_POSITIVE, "取消", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                pd.dismiss();// 关闭ProgressDialog
                                ota_flag = false;
                            }
                        });
                        pd.setCanceledOnTouchOutside(false);
                        pd.setMessage("正在更新固件,请勿断开设备电源!\n大约1分钟左右,请稍后....\n可以直接取消此窗口,不影响设备ota过程");
                        pd.show();
//                        handler.sendEmptyMessageDelayed(0,5000);

                    }
                }
                //endregion
            }
            //endregion


            //region 获取功率校准系数
            if (jsonObject.has("power_calibration")) {
                if (jsonObject.get("power_calibration") instanceof Integer
                        || jsonObject.get("power_calibration") instanceof Double) {
                    double power_calibration_val = jsonObject.getDouble("power_calibration");
                    power_calibration.setSummary(String.valueOf(power_calibration_val));
                    power_calibration.setText(String.valueOf(power_calibration_val));
                }
            }
            //endregion
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //region 事件监听调用函数,主要为在子类中重写此函数实现在service建立成功/mqtt连接成功/失败时执行功能
    //Service建立成功时调用    此函数需要时在子类中重写
    public void ServiceConnected() {
        handler.sendEmptyMessageDelayed(3, 0);
    }

    //mqtt连接成功时调用    此函数需要时在子类中重写
    public void MqttConnected() {
        handler.sendEmptyMessageDelayed(3, 0);
    }

    //mqtt连接断开时调用    此函数需要时在子类中重写
    public void MqttDisconnected() {
        handler.sendEmptyMessageDelayed(3, 0);
    }
    //endregion

}
