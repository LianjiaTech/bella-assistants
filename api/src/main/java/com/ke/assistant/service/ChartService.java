package com.ke.assistant.service;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.LineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.general.PieDataset;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 图表生成服务
 */
@Service
public class ChartService {

    /**
     * 生成柱状图
     * 
     * @param title 图表标题
     * @param categoryAxisLabel X轴标签
     * @param valueAxisLabel Y轴标签
     * @param data 数据
     * @param xAxisLabels X轴标签数组
     * @param width 图表宽度
     * @param height 图表高度
     * @return 图表的字节数组
     */
    public byte[] generateBarChart(String title, String categoryAxisLabel, String valueAxisLabel, 
                                 double[] data, String[] xAxisLabels, int width, int height) throws IOException {
        
        // 创建数据集
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (int i = 0; i < data.length; i++) {
            String category = (xAxisLabels != null && i < xAxisLabels.length) 
                ? xAxisLabels[i] 
                : "项目" + (i + 1);
            dataset.addValue(data[i], "数据", category);
        }
        
        // 创建柱状图
        JFreeChart chart = ChartFactory.createBarChart(
            title,                    // 图表标题
            categoryAxisLabel,        // X轴标签
            valueAxisLabel,           // Y轴标签
            dataset,                  // 数据集
            PlotOrientation.VERTICAL, // 图表方向
            false,                    // 是否显示图例
            true,                     // 是否显示工具提示
            false                     // 是否显示URL链接
        );
        
        // 设置图表样式
        customizeChart(chart);
        
        // 将图表转换为字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ChartUtils.writeChartAsJPEG(outputStream, chart, width, height);
        return outputStream.toByteArray();
    }
    
    /**
     * 生成折线图
     * 
     * @param title 图表标题
     * @param categoryAxisLabel X轴标签
     * @param valueAxisLabel Y轴标签
     * @param data 数据
     * @param xAxisLabels X轴标签数组
     * @param width 图表宽度
     * @param height 图表高度
     * @return 图表的字节数组
     */
    public byte[] generateLineChart(String title, String categoryAxisLabel, String valueAxisLabel, 
                                  double[] data, String[] xAxisLabels, int width, int height) throws IOException {
        
        // 创建数据集
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        for (int i = 0; i < data.length; i++) {
            String category = (xAxisLabels != null && i < xAxisLabels.length) 
                ? xAxisLabels[i] 
                : "项目" + (i + 1);
            dataset.addValue(data[i], "数据", category);
        }
        
        // 创建折线图
        JFreeChart chart = ChartFactory.createLineChart(
            title,                    // 图表标题
            categoryAxisLabel,        // X轴标签
            valueAxisLabel,           // Y轴标签
            dataset,                  // 数据集
            PlotOrientation.VERTICAL, // 图表方向
            false,                    // 是否显示图例
            true,                     // 是否显示工具提示
            false                     // 是否显示URL链接
        );
        
        // 设置图表样式
        customizeLineChart(chart);
        
        // 将图表转换为字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ChartUtils.writeChartAsJPEG(outputStream, chart, width, height);
        return outputStream.toByteArray();
    }
    
    /**
     * 生成饼图
     * 
     * @param title 图表标题
     * @param data 数据
     * @param labels 标签数组
     * @param width 图表宽度
     * @param height 图表高度
     * @return 图表的字节数组
     */
    public byte[] generatePieChart(String title, double[] data, String[] labels, int width, int height) throws IOException {
        
        // 创建数据集
        DefaultPieDataset<String> dataset = new DefaultPieDataset();
        
        for (int i = 0; i < data.length; i++) {
            String label = (labels != null && i < labels.length) 
                ? labels[i] 
                : "项目" + (i + 1);
            dataset.setValue(label, data[i]);
        }
        
        // 创建饼图
        JFreeChart chart = ChartFactory.createPieChart(
            title,      // 图表标题
            dataset,    // 数据集
            true,       // 是否显示图例
            true,       // 是否显示工具提示
            false       // 是否显示URL链接
        );
        
        // 设置图表样式
        customizePieChart(chart);
        
        // 将图表转换为字节数组
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ChartUtils.writeChartAsJPEG(outputStream, chart, width, height);
        return outputStream.toByteArray();
    }

    /**
     * 自定义柱状图样式
     */
    private void customizeChart(JFreeChart chart) {
        // 设置背景色
        chart.setBackgroundPaint(Color.WHITE);
        
        // 设置标题字体
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(new Font("SansSerif", Font.BOLD, 16));
            title.setPaint(Color.BLACK);
        }
        
        // 获取绘图区域
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // 设置柱状图渲染器
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(79, 129, 189)); // 设置柱子颜色
        renderer.setDrawBarOutline(false);
        
        // 设置坐标轴字体
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        domainAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        
        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        rangeAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
    }
    
    /**
     * 自定义折线图样式
     */
    private void customizeLineChart(JFreeChart chart) {
        // 设置背景色
        chart.setBackgroundPaint(Color.WHITE);
        
        // 设置标题字体
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(new Font("SansSerif", Font.BOLD, 16));
            title.setPaint(Color.BLACK);
        }
        
        // 获取绘图区域
        CategoryPlot plot = (CategoryPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinesVisible(true);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);
        
        // 设置折线图渲染器
        LineAndShapeRenderer renderer = (LineAndShapeRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(79, 129, 189)); // 设置线条颜色
        renderer.setSeriesStroke(0, new BasicStroke(2.0f)); // 设置线条粗细
        renderer.setSeriesShapesVisible(0, true); // 显示数据点
        renderer.setSeriesShapesFilled(0, true);
        
        // 设置坐标轴字体
        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        domainAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        
        ValueAxis rangeAxis = plot.getRangeAxis();
        rangeAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        rangeAxis.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
    }
    
    /**
     * 自定义饼图样式
     */
    @SuppressWarnings("unchecked")
    private void customizePieChart(JFreeChart chart) {
        // 设置背景色
        chart.setBackgroundPaint(Color.WHITE);
        
        // 设置标题字体
        TextTitle title = chart.getTitle();
        if (title != null) {
            title.setFont(new Font("SansSerif", Font.BOLD, 16));
            title.setPaint(Color.BLACK);
        }
        
        // 获取饼图绘图区域
        PiePlot<String> plot = (PiePlot<String>) chart.getPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlineVisible(false);
        plot.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));
        plot.setNoDataMessage("没有数据");
        plot.setCircular(false);
        plot.setLabelGap(0.02);


        // 设置饼图颜色 - 为不同的key分配不同的颜色
        PieDataset<String> dataset = plot.getDataset();
        List<String> keys = dataset.getKeys();
        
        // 预定义颜色数组
        Color[] colors = {
            new Color(79, 129, 189),   // 蓝色
            new Color(192, 80, 77),    // 红色
            new Color(155, 187, 89),   // 绿色
            new Color(128, 100, 162),  // 紫色
            new Color(75, 172, 198),   // 青色
            new Color(247, 150, 70),   // 橙色
            new Color(146, 208, 80),   // 浅绿色
            new Color(255, 192, 0),    // 黄色
            new Color(153, 115, 0),    // 棕色
            new Color(112, 173, 71)    // 深绿色
        };
        
        // 为每个key分配颜色
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            Color color = colors[i % colors.length]; // 循环使用颜色
            plot.setSectionPaint(key, color);
        }
    }
}
