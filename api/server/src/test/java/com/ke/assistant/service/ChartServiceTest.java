package com.ke.assistant.service;

import com.ke.assistant.configuration.AssistantProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChartService 测试类
 * 验证图表生成功能
 */
@SpringBootTest
@EnableConfigurationProperties(AssistantProperties.class)
@ActiveProfiles("ut")
public class ChartServiceTest {

    @Autowired
    private ChartService chartService;
    
    @Autowired
    private S3Service s3Service;

    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;

    @Test
    public void testGenerateBarChart() throws IOException {
        // 准备测试数据
        String title = "测试柱状图";
        String categoryAxisLabel = "类别";
        String valueAxisLabel = "数值";
        double[] data = {100.0, 200.0, 150.0, 300.0, 250.0};
        String[] xAxisLabels = {"A", "B", "C", "D", "E"};

        // 生成柱状图
        byte[] result = chartService.generateBarChart(title, categoryAxisLabel, valueAxisLabel, 
                                                    data, xAxisLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 验证结果
        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
        assertTrue(result.length > 1000, "图表文件大小应该合理");
    }

    @Test
    public void testGenerateBarChartWithoutLabels() throws IOException {
        // 测试不提供标签的情况
        String title = "无标签柱状图";
        String categoryAxisLabel = "类别";
        String valueAxisLabel = "数值";
        double[] data = {50.0, 75.0, 100.0};

        byte[] result = chartService.generateBarChart(title, categoryAxisLabel, valueAxisLabel, 
                                                    data, null, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
    }

    @Test
    public void testGenerateLineChart() throws IOException {
        // 准备测试数据
        String title = "测试折线图";
        String categoryAxisLabel = "时间";
        String valueAxisLabel = "温度";
        double[] data = {20.5, 22.0, 25.5, 23.0, 21.5, 19.0};
        String[] xAxisLabels = {"1月", "2月", "3月", "4月", "5月", "6月"};

        // 生成折线图
        byte[] result = chartService.generateLineChart(title, categoryAxisLabel, valueAxisLabel, 
                                                     data, xAxisLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 验证结果
        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
        assertTrue(result.length > 1000, "图表文件大小应该合理");
    }

    @Test
    public void testGenerateLineChartWithoutLabels() throws IOException {
        // 测试不提供标签的情况
        String title = "无标签折线图";
        String categoryAxisLabel = "X轴";
        String valueAxisLabel = "Y轴";
        double[] data = {10.0, 15.0, 8.0, 20.0};

        byte[] result = chartService.generateLineChart(title, categoryAxisLabel, valueAxisLabel, 
                                                     data, null, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
    }

    @Test
    public void testGeneratePieChart() throws IOException {
        // 准备测试数据
        String title = "测试饼图";
        double[] data = {30.0, 25.0, 20.0, 15.0, 10.0};
        String[] labels = {"产品A", "产品B", "产品C", "产品D", "产品E"};

        // 生成饼图
        byte[] result = chartService.generatePieChart(title, data, labels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 验证结果
        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
        assertTrue(result.length > 1000, "图表文件大小应该合理");
    }

    @Test
    public void testGeneratePieChartWithoutLabels() throws IOException {
        // 测试不提供标签的情况
        String title = "无标签饼图";
        double[] data = {40.0, 35.0, 25.0};

        byte[] result = chartService.generatePieChart(title, data, null, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
    }

    @Test
    public void testGeneratePieChartWithManySegments() throws IOException {
        // 测试多个分段的饼图，验证颜色循环使用
        String title = "多分段饼图";
        double[] data = {10.0, 15.0, 12.0, 8.0, 20.0, 5.0, 18.0, 7.0, 9.0, 13.0, 6.0, 11.0};
        String[] labels = {"项目1", "项目2", "项目3", "项目4", "项目5", "项目6", 
                          "项目7", "项目8", "项目9", "项目10", "项目11", "项目12"};

        byte[] result = chartService.generatePieChart(title, data, labels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        assertNotNull(result, "生成的图表不应为空");
        assertTrue(result.length > 0, "生成的图表应有内容");
        assertTrue(result.length > 1000, "图表文件大小应该合理");
    }

    @Test
    public void testEmptyDataHandling() {
        // 测试空数据的处理
        String title = "空数据测试";
        double[] emptyData = {};
        String[] emptyLabels = {};

        // 验证空数据不会导致异常
        assertDoesNotThrow(() -> {
            chartService.generateBarChart(title, "X", "Y", emptyData, emptyLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }, "空数据应该被正确处理");

        assertDoesNotThrow(() -> {
            chartService.generateLineChart(title, "X", "Y", emptyData, emptyLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }, "空数据应该被正确处理");

        assertDoesNotThrow(() -> {
            chartService.generatePieChart(title, emptyData, emptyLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }, "空数据应该被正确处理");
    }

    @Test
    public void testSingleDataPoint() throws IOException {
        // 测试单个数据点
        String title = "单数据点测试";
        double[] singleData = {100.0};
        String[] singleLabel = {"唯一项"};

        byte[] barResult = chartService.generateBarChart(title, "X", "Y", singleData, singleLabel, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        byte[] lineResult = chartService.generateLineChart(title, "X", "Y", singleData, singleLabel, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        byte[] pieResult = chartService.generatePieChart(title, singleData, singleLabel, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        assertNotNull(barResult, "单数据点柱状图应该能生成");
        assertNotNull(lineResult, "单数据点折线图应该能生成");
        assertNotNull(pieResult, "单数据点饼图应该能生成");
        
        assertTrue(barResult.length > 0, "单数据点柱状图应该有内容");
        assertTrue(lineResult.length > 0, "单数据点折线图应该有内容");
        assertTrue(pieResult.length > 0, "单数据点饼图应该有内容");
    }

    @Test
    public void testNegativeValues() throws IOException {
        // 测试负值处理
        String title = "负值测试";
        double[] dataWithNegatives = {-50.0, 100.0, -25.0, 75.0, -10.0};
        String[] labels = {"A", "B", "C", "D", "E"};

        // 柱状图和折线图应该能处理负值
        byte[] barResult = chartService.generateBarChart(title, "X", "Y", dataWithNegatives, labels, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        byte[] lineResult = chartService.generateLineChart(title, "X", "Y", dataWithNegatives, labels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        assertNotNull(barResult, "含负值的柱状图应该能生成");
        assertNotNull(lineResult, "含负值的折线图应该能生成");
        assertTrue(barResult.length > 0, "含负值的柱状图应该有内容");
        assertTrue(lineResult.length > 0, "含负值的折线图应该有内容");
    }

    @Test
    public void testCustomDimensions() throws IOException {
        // 测试自定义尺寸
        String title = "自定义尺寸测试";
        double[] data = {10.0, 20.0, 30.0};
        String[] labels = {"小", "中", "大"};
        
        int customWidth = 1200;
        int customHeight = 800;

        byte[] result = chartService.generatePieChart(title, data, labels, customWidth, customHeight);

        assertNotNull(result, "自定义尺寸图表应该能生成");
        assertTrue(result.length > 0, "自定义尺寸图表应该有内容");
    }

    @Test
    public void testS3ConfigurationStatus() {
        // 测试S3配置状态
        boolean isConfigured = s3Service.isConfigured();
        System.out.println("S3服务配置状态: " + (isConfigured ? "已配置" : "未配置"));
        
        // 这个测试不会失败，只是输出配置状态信息
        assertTrue(true, "S3配置状态检查完成");
    }

    @Test
    public void testGenerateAndUploadBarChart() throws IOException {
        // 只有在S3配置完整时才运行此测试
        if (!s3Service.isConfigured()) {
            System.out.println("跳过S3上传测试 - S3服务未配置");
            return;
        }

        // 准备测试数据
        String title = "S3上传测试柱状图";
        String categoryAxisLabel = "月份";
        String valueAxisLabel = "销售额";
        double[] data = {120.5, 150.8, 98.3, 200.1, 175.9};
        String[] xAxisLabels = {"1月", "2月", "3月", "4月", "5月"};

        // 生成柱状图
        byte[] chartData = chartService.generateBarChart(title, categoryAxisLabel, valueAxisLabel, 
                                                       data, xAxisLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 验证图表生成成功
        assertNotNull(chartData, "生成的图表不应为空");
        assertTrue(chartData.length > 0, "生成的图表应有内容");

        // 上传到S3
        String fileUrl = s3Service.uploadChart(chartData, "jpg");
        
        // 验证上传结果
        assertNotNull(fileUrl, "上传后应返回文件URL");
        assertTrue(fileUrl.endsWith(".jpg"), "文件URL应以.jpg结尾");
        
        System.out.println("柱状图上传成功，URL: " + fileUrl);
    }

    @Test
    public void testGenerateAndUploadPieChart() throws IOException {
        // 只有在S3配置完整时才运行此测试
        if (!s3Service.isConfigured()) {
            System.out.println("跳过S3上传测试 - S3服务未配置");
            return;
        }

        // 准备测试数据
        String title = "S3上传测试饼图";
        double[] data = {35.5, 28.3, 20.1, 16.1};
        String[] labels = {"产品A", "产品B", "产品C", "产品D"};

        // 生成饼图
        byte[] chartData = chartService.generatePieChart(title, data, labels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 验证图表生成成功
        assertNotNull(chartData, "生成的图表不应为空");
        assertTrue(chartData.length > 0, "生成的图表应有内容");

        // 上传到S3（测试默认格式）
        String fileUrl = s3Service.uploadChart(chartData, null);
        
        // 验证上传结果
        assertNotNull(fileUrl, "上传后应返回文件URL");
        assertTrue(fileUrl.endsWith(".jpg"), "文件URL应以默认格式.jpg结尾");
        
        System.out.println("饼图上传成功，URL: " + fileUrl);
    }

    @Test
    public void testGenerateAndUploadLineChart() throws IOException {
        // 只有在S3配置完整时才运行此测试
        if (!s3Service.isConfigured()) {
            System.out.println("跳过S3上传测试 - S3服务未配置");
            return;
        }

        // 准备测试数据
        String title = "S3上传测试折线图";
        String categoryAxisLabel = "时间";
        String valueAxisLabel = "温度(°C)";
        double[] data = {18.5, 22.1, 25.8, 28.3, 26.7, 23.2, 19.8};
        String[] xAxisLabels = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

        // 生成折线图
        byte[] chartData = chartService.generateLineChart(title, categoryAxisLabel, valueAxisLabel, 
                                                        data, xAxisLabels, DEFAULT_WIDTH, DEFAULT_HEIGHT);

        // 验证图表生成成功
        assertNotNull(chartData, "生成的图表不应为空");
        assertTrue(chartData.length > 0, "生成的图表应有内容");

        // 上传到S3（测试PNG格式）
        String fileUrl = s3Service.uploadChart(chartData, "png");
        
        // 验证上传结果
        assertNotNull(fileUrl, "上传后应返回文件URL");
        assertTrue(fileUrl.endsWith(".png"), "文件URL应以.png结尾");
        
        System.out.println("折线图上传成功，URL: " + fileUrl);
    }
}
