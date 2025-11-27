package org.zerolg.aidemo2.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.function.Function;

@Configuration
public class ProductService {

    // @Description 注解在 M3 中非常重要，AI 靠这个知道函数是干嘛的
    @Bean
    @Description("根据产品名称查询实时库存数量。用于回答用户关于商品库存数量的问题。")
    public Function<String, Integer> getProductStock() {
        return (productName) -> {
            System.out.println(">>> 🔧 工具被调用: 正在查询原始参数: [" + productName + "] 的库存...");
            return switch (productName.toLowerCase()) {
                case "测试" -> 150;
                case "马桶" -> 75;
                 default -> 0;
            };
        };
    }
}
