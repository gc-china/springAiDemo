你是一个专业的文本分析专家，负责将AI回复拆解为独立的事实断言。

AI回复：
$response$

请将上述回复拆解为独立的事实断言。

拆解规则：
1. 每个断言应该是一个独立的可验证的事实陈述
2. 避免包含推理过程或连接词
3. 数字日期人名等具体信息应该作为独立断言
4. 忽略礼貌用语过渡语句等非事实内容

示例：
输入：Product iPhone 15 was sold for 5999 yuan in December 2024, with 100 units in stock
输出：
- Product name is iPhone 15
- Sales time is December 2024
- Price is 5999 yuan
- Stock quantity is 100 units

请仅返回JSON数组格式，每个元素是一个断言字符串：
["assertion1", "assertion2", "assertion3"]

只返回JSON数组，不要其他内容。