package com.flag.oi_experiment.customComponent;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import com.flag.oi_experiment.R;
import com.flag.oi_experiment.tcpUtils.IOBlockedRunnable;

import java.util.LinkedList;
import java.util.List;

import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.ValueShape;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;

/**
 * Created by Bmind on 2018/6/19.
 */

public class CollectLayout extends LinearLayout {
    public String TAG = "CollectLayout";
    private LinearLayout collectContainer;
    private ImageView imageType;
    private TextView showLink;
    private boolean isCollectSensor = false;        //是否是采集传感器
    private String bindipAddress;                   //当前该传感器连接的IP地址
    public String sensorType;
    private String number;
    private int tableWidth;     //记录屏幕宽和高
    private int tableHeight;
    private Activity mActivity;
    private TextView temshowValue;
    private TextView humshowValue;

    private TextView pm25,pm25lz,pm10,pm10lz;

    PopupWindow popup;
    IOBlockedRunnable run;

    private ImageView iv_hasornot = null;


    public String getSensorType() {
        return sensorType;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    public String getNumber() {
        return number;
    }

    public CollectLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.collect,this);
        showLink = (TextView) findViewById(R.id.showLink);
        imageType = (ImageView) findViewById(R.id.imageType);
        collectContainer = (LinearLayout) findViewById(R.id.collectContainer);


        collectContainer.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
    }
    public void setShowInit(String type, String wifiOrZigbee, String ipAddress, String number){       //设置第一次连接UI显示状态
        int imageId = 0;
        this.bindipAddress = ipAddress;         //设置IP方便索引到Runnable对象
        this.sensorType = type;
        this.number = number;
        switch (type){
            case "温湿度":imageId = R.drawable.temhum;isCollectSensor = true;break;
            case "光照度":imageId = R.drawable.guangmin;isCollectSensor = true;break;
            case "风扇":imageId = R.drawable.fengshan;break;
            /**cssf新增应用**/
            case "电磁锁":imageId = R.drawable.lock;break;
            case "可调灯":imageId = R.drawable.light;break;
            case "继电器":imageId = R.drawable.switchp;break;
            case "全向红外":imageId = R.drawable.quanxiang;break;
            case "声光报警":imageId = R.drawable.sounglight;break;

            case "人体红外":imageId = R.drawable.bodyinfrared;isCollectSensor = true;break;
            case "红外对射":imageId = R.drawable.infraredfence;isCollectSensor = true;break;
            case "可燃气体":imageId = R.drawable.gas;isCollectSensor = true;break;
            case "火焰气体":imageId = R.drawable.flame;isCollectSensor = true;break;
        }
        if (imageId != 0){
            imageType.setBackgroundResource(imageId);
        }
        String orignal = number+"当前连接"+wifiOrZigbee;
        SpannableStringBuilder style=new SpannableStringBuilder(orignal);
        style.setSpan(new ForegroundColorSpan(Color.YELLOW),6,orignal.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        showLink.setText(style);
    }

    public void changeStatueLink(String type, String ipAddress){   //改变连接显示状态
        if (!type.equals("")){
            String orignal = number+"当前连接"+type;
            this.bindipAddress = ipAddress;                             //更改当前连接的ip 信息
            SpannableStringBuilder style=new SpannableStringBuilder(orignal);
            style.setSpan(new ForegroundColorSpan(Color.YELLOW),6,orignal.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            showLink.setText(style);
            closeUIPopWindow();
        }
    }

    public void changeStatueLinkNoPOP(String type, String ipAddress){   //改变连接显示状态
        if (!type.equals("")){
            String orignal = number+"当前连接"+type;
            this.bindipAddress = ipAddress;                             //更改当前连接的ip 信息
            SpannableStringBuilder style=new SpannableStringBuilder(orignal);
            style.setSpan(new ForegroundColorSpan(Color.YELLOW),6,orignal.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            showLink.setText(style);
        }
    }

    public void setConfig(Activity ac, int tableWidth, int tableHeight){
        this.mActivity = ac;
        this.tableWidth = tableWidth;
        this.tableHeight = tableHeight;
    }

    private LineChartView charTem;
    private Axis axisY;                     //Y坐标
    private Axis axisX;                     //X坐标
    private LinkedList mPointValue;         //点值链表
    private List<Line> mLine;               //线条列表
    private float position = 0;             //横坐标位置

    private LineChartView charHum;
    private Axis axisY_hum;                     //Y坐标
    private Axis axisX_hum;                     //X坐标
    private LinkedList mPointValue_hum;         //点值链表
    private List<Line> mLine_hum;               //线条列表
    private float position_hum = 0;             //横坐标位置0




    //更新折线图
    private void pointUpdate(Float value){
        float x = 0;
        PointValue p = new PointValue(position,value);
        position++;
        mPointValue.add(p);
        x = p.getX();
        Line line = new Line(mPointValue).setColor(Color.parseColor("#f4a00d"));//设置线条颜色
        line.setShape(ValueShape.CIRCLE);//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.SQUARE）
        line.setCubic(true);//曲线是否平滑
        line.setStrokeWidth(1);//线条的粗细，默认是1
        line.setFilled(false);//是否填充曲线的面积
        line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(true);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setPointColor(Color.parseColor("#f4a00d"));    //设置point点的颜色
        line.setPointRadius(3);         //设置圆点半径长度

        mLine.add(line);
        LineChartData data = new LineChartData(mLine);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        charTem.setLineChartData(data);

        Viewport port = new Viewport(charTem.getMaximumViewport());
        port.top = 40;              //设置top和bottom，纵坐标的最大值
        port.bottom = 0;
        if(x>8){                            //横向显示7个数据
            port.top = 40;              //无需设置top和bottom，让他默认就行
            port.bottom = 0;
            port.left = x-8;
            port.right = x;
        }
        charTem.setMaximumViewport(port);
        charTem.setCurrentViewport(port);
    }
    private void pointUpdate_hum(Float value){
        float x = 0;
        PointValue p = new PointValue(position_hum,value);
        position_hum++;
        mPointValue_hum.add(p);
        x = p.getX();
        Line line = new Line(mPointValue_hum).setColor(Color.parseColor("#f4a00d"));//设置线条颜色
        line.setShape(ValueShape.CIRCLE);//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.SQUARE）
        line.setCubic(true);//曲线是否平滑
        line.setStrokeWidth(1);//线条的粗细，默认是1
        line.setFilled(false);//是否填充曲线的面积
        line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(true);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setPointColor(Color.parseColor("#f4a00d"));    //设置point点的颜色
        line.setPointRadius(3);         //设置圆点半径长度

        mLine_hum.add(line);
        LineChartData data = new LineChartData(mLine_hum);
        data.setAxisXBottom(axisX_hum);
        data.setAxisYLeft(axisY_hum);
        charHum.setLineChartData(data);

        Viewport port = new Viewport(charHum.getMaximumViewport());
        port.top = 100;              //设置top和bottom，纵坐标的最大值
        port.bottom = 0;
        if(x>8){                            //横向显示7个数据
            port.top = 100;              //无需设置top和bottom，让他默认就行
            port.bottom = 0;
            port.left = x-8;
            port.right = x;
        }
        charHum.setMaximumViewport(port);
        charHum.setCurrentViewport(port);
    }
    private void pointUpdate_guang(Float value){
        float x = 0;
        PointValue p = new PointValue(position,value);
        position++;
        mPointValue.add(p);
        x = p.getX();
        Line line = new Line(mPointValue).setColor(Color.parseColor("#f4a00d"));//设置线条颜色
        line.setShape(ValueShape.CIRCLE);//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.SQUARE）
        line.setCubic(true);//曲线是否平滑
        line.setStrokeWidth(1);//线条的粗细，默认是1
        line.setFilled(false);//是否填充曲线的面积
        line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(true);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setPointColor(Color.parseColor("#f4a00d"));    //设置point点的颜色
        line.setPointRadius(3);         //设置圆点半径长度

        mLine.add(line);
        LineChartData data = new LineChartData(mLine);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        charTem.setLineChartData(data);

        Viewport port = new Viewport(charTem.getMaximumViewport());
        if (rangeInDefined(value,0,500))
            port.top = 500;
        else if (rangeInDefined(value,0,1000))
            port.top = 1000;
        else if (rangeInDefined(value,1000,10000))
            port.top = 10000;
        else if (rangeInDefined(value,10000,30000))
            port.top = 30000;
        else if (rangeInDefined(value,30000,60000))
            port.top = 60000;
        else if (rangeInDefined(value,60000,65525))
            port.top = 65525;
        port.bottom = 0;
        if(x>8){                            //横向显示7个数据
//            port.top = 65535;              //无需设置top和bottom，让他默认就行
//            port.bottom = 0;
            port.left = x-8;
            port.right = x;
        }
        charTem.setMaximumViewport(port);
        charTem.setCurrentViewport(port);
    }
    private void pointUpdate_co2(Float value){
        float x = 0;
        PointValue p = new PointValue(position,value);
        position++;
        mPointValue.add(p);
        x = p.getX();
        Line line = new Line(mPointValue).setColor(Color.parseColor("#f4a00d"));//设置线条颜色
        line.setShape(ValueShape.CIRCLE);//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.SQUARE）
        line.setCubic(true);//曲线是否平滑
        line.setStrokeWidth(1);//线条的粗细，默认是1
        line.setFilled(false);//是否填充曲线的面积
        line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(true);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setPointColor(Color.parseColor("#f4a00d"));    //设置point点的颜色
        line.setPointRadius(3);         //设置圆点半径长度

        mLine.add(line);
        LineChartData data = new LineChartData(mLine);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        charTem.setLineChartData(data);

        Viewport port = new Viewport(charTem.getMaximumViewport());
        if (rangeInDefined(value,0,500))
            port.top = 500;
        else if (rangeInDefined(value,0,1000))
            port.top = 1000;
        else if (rangeInDefined(value,1000,10000))
            port.top = 10000;
        else if (rangeInDefined(value,10000,30000))
            port.top = 30000;
        else if (rangeInDefined(value,30000,60000))
            port.top = 60000;
        else if (rangeInDefined(value,60000,65525))
            port.top = 65525;
        port.bottom = 0;
        if(x>8){                            //横向显示7个数据
//            port.top = 65535;              //无需设置top和bottom，让他默认就行
//            port.bottom = 0;
            port.left = x-8;
            port.right = x;
        }
        charTem.setMaximumViewport(port);
        charTem.setCurrentViewport(port);
    }
    private void pointUpdate_o2(Float value){
        float x = 0;
        PointValue p = new PointValue(position,value);
        position++;
        mPointValue.add(p);
        x = p.getX();
        Line line = new Line(mPointValue).setColor(Color.parseColor("#f4a00d"));//设置线条颜色
        line.setShape(ValueShape.CIRCLE);//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.SQUARE）
        line.setCubic(true);//曲线是否平滑
        line.setStrokeWidth(1);//线条的粗细，默认是1
        line.setFilled(false);//是否填充曲线的面积
        line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(true);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setPointColor(Color.parseColor("#f4a00d"));    //设置point点的颜色
        line.setPointRadius(3);         //设置圆点半径长度

        mLine.add(line);
        LineChartData data = new LineChartData(mLine);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        charTem.setLineChartData(data);

        Viewport port = new Viewport(charTem.getMaximumViewport());
        if (rangeInDefined(value,0,100))
            port.top = 100;
        else if (rangeInDefined(value,0,500))
            port.top = 500;
        else if (rangeInDefined(value,0,1000))
            port.top = 1000;
        else if (rangeInDefined(value,1000,10000))
            port.top = 10000;
        else if (rangeInDefined(value,10000,30000))
            port.top = 30000;
        else if (rangeInDefined(value,30000,60000))
            port.top = 60000;
        else if (rangeInDefined(value,60000,65525))
            port.top = 65525;
        port.bottom = 0;
        if(x>8){                            //横向显示7个数据
            port.left = x-8;
            port.right = x;
        }
        charTem.setMaximumViewport(port);
        charTem.setCurrentViewport(port);
    }
    private void pointUpdate_pa(Float value){
        float x = 0;
        PointValue p = new PointValue(position,value);
        position++;
        mPointValue.add(p);
        x = p.getX();
        Line line = new Line(mPointValue).setColor(Color.parseColor("#f4a00d"));//设置线条颜色
        line.setShape(ValueShape.CIRCLE);//折线图上每个数据点的形状  这里是圆形 （有三种 ：ValueShape.SQUARE  ValueShape.CIRCLE  ValueShape.SQUARE）
        line.setCubic(true);//曲线是否平滑
        line.setStrokeWidth(1);//线条的粗细，默认是1
        line.setFilled(false);//是否填充曲线的面积
        line.setHasLabels(true);//曲线的数据坐标是否加上备注
        line.setHasLines(true);//是否用直线显示。如果为false 则没有曲线只有点显示
        line.setHasPoints(true);//是否显示圆点 如果为false 则没有原点只有点显示
        line.setPointColor(Color.parseColor("#f4a00d"));    //设置point点的颜色
        line.setPointRadius(3);         //设置圆点半径长度

        mLine.add(line);
        LineChartData data = new LineChartData(mLine);
        data.setAxisXBottom(axisX);
        data.setAxisYLeft(axisY);
        charTem.setLineChartData(data);

        Viewport port = new Viewport(charTem.getMaximumViewport());
        if (rangeInDefined(value,0,500))
            port.top = 500;
        else if (rangeInDefined(value,0,1000))
            port.top = 1000;
        else if (rangeInDefined(value,1000,10000))
            port.top = 10000;
        else if (rangeInDefined(value,10000,30000))
            port.top = 30000;
        else if (rangeInDefined(value,30000,60000))
            port.top = 60000;
        else if (rangeInDefined(value,60000,65525))
            port.top = 65525;
        port.bottom = 0;
        if(x>8){                            //横向显示7个数据
            port.left = x-8;
            port.right = x;
        }
        charTem.setMaximumViewport(port);
        charTem.setCurrentViewport(port);
    }

    //确定是否在两数之间
    public  boolean rangeInDefined(float current, float min, float max)
    {
        return Math.max(min, current) == Math.min(current, max);
    }

    public void updateChartTemHun(float tem, float hum){        //更新温湿度折线图值
        pointUpdate(tem);
        pointUpdate_hum(hum);
        String temStr = "温度值："+tem;
        SpannableStringBuilder style=new SpannableStringBuilder(temStr+"℃");
        style.setSpan(new ForegroundColorSpan(Color.YELLOW),4,temStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        temshowValue.setText(style);
        temStr = "湿度值："+hum;
        SpannableStringBuilder style1=new SpannableStringBuilder(temStr+"RH");
        style1.setSpan(new ForegroundColorSpan(Color.YELLOW),4,temStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        humshowValue.setText(style1);

    }
    public void updateChartGuang(float guang){              //更新光照度折线图值
        pointUpdate_guang(guang);
        String temStr = "光照度："+guang;
        SpannableStringBuilder style=new SpannableStringBuilder(temStr+"勒克斯");
        style.setSpan(new ForegroundColorSpan(Color.YELLOW),4,temStr.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        temshowValue.setText(style);
    }
    //关闭popwindow 弹窗
    public void closeUIPopWindow(){
//        if (popup != null){
//            popup.dismiss();
//            popup = null;
//        }

    }

    //解析数据,更新折线图
    public void updateChartData(String dataContext){
        try{
            switch (this.sensorType) {
                case "温湿度":
                    String datatemhum = dataContext.substring(9,9 + Integer.parseInt(dataContext.substring(7,9)));
                    if (datatemhum.contains("t")&&datatemhum.contains("h")){
                        int indexhum = datatemhum.indexOf("h");             //datatemhun 值：t+27.1h65.1
                        float temvalue = Float.parseFloat(datatemhum.substring(1,indexhum));
                        float humvalue = Float.parseFloat(datatemhum.substring(1+indexhum));
                        updateChartTemHun(temvalue, humvalue);
                    }
                    break;
                case "光照度":

                    String dataillu = dataContext.substring(9,9 + Integer.parseInt(dataContext.substring(7,9)));
                    updateChartGuang(Float.parseFloat(dataillu));
                    break;
                case "人体红外":
                case "红外对射":
                case "可燃气体":
                case "火焰气体":
                    if (iv_hasornot != null){
                        String hasOrnot = dataContext.substring(9,9 + Integer.parseInt(dataContext.substring(7,9)));
                        iv_hasornot.setImageResource(hasOrnot.equals("0")?R.drawable.hasfalse:R.drawable.hastrue);
                    }
                    break;

            }
        }catch (Exception e){
           e.printStackTrace();
        }
    }

}
