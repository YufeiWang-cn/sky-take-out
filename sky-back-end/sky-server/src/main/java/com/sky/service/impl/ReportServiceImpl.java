package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WorkspaceService workspaceService;

    /**
     * 统计指定时间区间内的营业额数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        // begin小于等于end
        while(!begin.isAfter(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }

        // 存放每天的营业额
        List<Double> turnoverList = new ArrayList<>();

        // 查询date对应的营业额数据
        LocalDateTime beginTime, endTime;
        for(LocalDate date : dateList) {
            beginTime = LocalDateTime.of(date, LocalTime.MIN);
            endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);
            Double turnover = orderMapper.sumByMap(map);
            turnover = (turnover == null) ? 0.0 : turnover;
            turnoverList.add(turnover);
        }

        TurnoverReportVO turnoverReportVO = TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .turnoverList(StringUtils.join(turnoverList, ","))
                .build();
        return turnoverReportVO;
    }

    /**
     * 统计指定时间区间内的用户数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        // begin小于等于end
        while(!begin.isAfter(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }

        // 用户总量
        List<Integer> totalUserList = new ArrayList<>();
        // 新增用户
        List<Integer> newUserList = new ArrayList<>();

        LocalDateTime beginTime, endTime;
        for(LocalDate date : dateList) {
            beginTime = LocalDateTime.of(date, LocalTime.MIN);
            endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("end", endTime);
            Integer totalUser = userMapper.countByMap(map);
            totalUserList.add(totalUser);

            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);
            newUserList.add(newUser);
        }

        UserReportVO userReportVO = UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .build();
        return userReportVO;
    }

    /**
     * 统计指定时间区间内的订单数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        List<LocalDate> dateList = new ArrayList<>();
        // begin小于等于end
        while(!begin.isAfter(end)) {
            dateList.add(begin);
            begin = begin.plusDays(1);
        }

        // 每日订单数
        List<Integer> orderCountList = new ArrayList<>();
        // 每日有效订单数
        List<Integer> validOrderCountList = new ArrayList<>();

        LocalDateTime beginTime, endTime;
        for(LocalDate date : dateList) {
            beginTime = LocalDateTime.of(date, LocalTime.MIN);
            endTime = LocalDateTime.of(date, LocalTime.MAX);
            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            Integer orderCount = orderMapper.countByMap(map);
            orderCountList.add(orderCount);

            map.put("status", Orders.COMPLETED);
            Integer validOrderCount = orderMapper.countByMap(map);
            validOrderCountList.add(validOrderCount);
        }

        // 计算时间区间内的订单总数量
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();

        // 计算时间区间内的有效订单数量
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();

        // 计算订单完成率
        Double orderCompletionRate = 0.0;
        if(totalOrderCount > 0) {
            orderCompletionRate = validOrderCount * 1.0 / totalOrderCount;
        }

        OrderReportVO orderReportVO = OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
        return orderReportVO;
    }

    /**
     * 统计指定时间区间内的销量排名top10
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getSalesTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);

        List<GoodsSalesDTO> salesTop10List = orderMapper.getSalesTop10(beginTime, endTime);

        List<String> nameList = salesTop10List.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numberList = salesTop10List.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        SalesTop10ReportVO salesTop10ReportVO = SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(nameList, ","))
                .numberList(StringUtils.join(numberList, ","))
                .build();
        return salesTop10ReportVO;
    }

    /**
     * 导出运营数据报表
     * @param response
     */
    @Override
    public void exportBusinessData(HttpServletResponse response) {
        // 查询数据库，获取营业数据——查询最近30天的运营数据，不包含今天
        LocalDate dateBegin = LocalDate.now().plusDays(-30);
        LocalDate dateEnd = LocalDate.now().plusDays(-1);
        // 查询概览数据
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));

        // 通过POI将数据写入到excel文件中
        // 加载模板
        InputStream in = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        // 基于模板文件创建一个新的excel文件
        try {
            assert in != null;
            XSSFWorkbook excel = new XSSFWorkbook(in);
            // 获取表格文件的Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            // 填充数据——时间
            sheet.getRow(1).getCell(1).setCellValue("时间：" + dateBegin + "至" + dateEnd);
            // 填充数据——营业额
            sheet.getRow(3).getCell(2).setCellValue(businessDataVO.getTurnover());
            // 填充数据————订单完成率
            sheet.getRow(3).getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            // 填充数据——新增用户数
            sheet.getRow(3).getCell(6).setCellValue(businessDataVO.getNewUsers());
            // 填充数据——有效订单
            sheet.getRow(4).getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            // 填充数据——平均客单价
            sheet.getRow(4).getCell(4).setCellValue(businessDataVO.getUnitPrice());

            // 填充明细数据
            LocalDate date;
            for(int i = 0; i < 30; i++) {
                date = dateBegin.plusDays(i);
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));
                sheet.getRow(7 + i).getCell(1).setCellValue(date.toString());
                sheet.getRow(7 + i).getCell(2).setCellValue(businessData.getTurnover());
                sheet.getRow(7 + i).getCell(3).setCellValue(businessData.getValidOrderCount());
                sheet.getRow(7 + i).getCell(4).setCellValue(businessData.getOrderCompletionRate());
                sheet.getRow(7 + i).getCell(5).setCellValue(businessData.getUnitPrice());
                sheet.getRow(7 + i).getCell(6).setCellValue(businessData.getNewUsers());
            }

            // 通过输出流将excel文件下载到客户端浏览器
            ServletOutputStream out = response.getOutputStream();
            excel.write(out);

            // 关闭资源
            out.close();
            excel.close();
        } catch(IOException e) {
            e.printStackTrace();
        }
    }
}