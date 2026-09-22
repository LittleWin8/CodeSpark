<template>
  <div id="termsPage">
    <h1>用户协议与免责声明</h1>
    <p class="updated">最近更新：2026-09-22</p>

    <h2>一、服务性质</h2>
    <p>
      本站（{{ siteName }}）为个人学习与实践性质的非经营性网站，由站长个人免费提供，
      用于展示人工智能应用开发的学习成果。本站不提供任何收费服务，不开展经营性活动。
    </p>

    <h2>二、账号与使用规范</h2>
    <ul>
      <li>注册仅用于学习与体验，请勿批量注册、自动化注册或使用爬虫等工具抓取本站内容。</li>
      <li>请勿利用本站生成、传播任何违反法律法规、侵犯他人权益或损害他人合法权益的内容。</li>
      <li>站长有权对明显滥用（如高频自动化调用）的账号采取限制措施。</li>
    </ul>

    <h2>三、用户内容与责任</h2>
    <p>
      您在本站创建、生成或发布的内容，其责任由您本人承担。
      您应确保自己的内容合法合规，并自行处理因内容产生的相关事宜。
    </p>

    <h2>四、AI 生成内容免责声明</h2>
    <p>
      本站的应用内容主要由人工智能模型自动生成，可能存在错误、遗漏或过时信息，
      <strong>仅供参考，不构成任何专业建议</strong>（包括但不限于法律、医疗、金融、投资等领域）。
      您基于生成内容作出的任何决定与行动，风险由您自行承担。
    </p>

    <h2>五、使用额度与数据清理</h2>
    <p>
      由于 AI 服务会产生实际成本，本站对使用额度设有合理限制；为控制资源占用，
      您生成的部分内容可能在一段时间后被清理，重要内容请自行备份。
    </p>

    <h2>六、服务可用性</h2>
    <p>
      本站为个人项目，不对服务的持续性、及时性作任何承诺。因维护、调整或不可抗力导致服务中断的，
      站长将尽量提前或及时说明，但不承担由此产生的损失。
    </p>

    <h2>七、知识产权</h2>
    <p>本站的界面设计、源代码归站长所有；您使用本站生成的内容归您本人使用，请遵守所引用开源组件的许可协议。</p>

    <h2>八、协议的变更</h2>
    <p>本协议如有修订，将在本页面更新并标注日期。继续使用本站即视为您接受修订后的协议。</p>

    <h2>九、联系站长</h2>
    <p>如有任何疑问，可通过邮箱联系：<a href="mailto:1944252183@qq.com">1944252183@qq.com</a></p>

    <p class="back"><router-link to="/">← 返回首页</router-link></p>
  </div>
</template>

<script setup lang="ts">
const siteName = 'CodeSpark AI'
</script>

<style scoped>
#termsPage {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px 16px 96px;
}
h1 {
  font-size: 24px;
  margin-bottom: 4px;
}
.updated {
  color: #999;
  font-size: 12px;
  margin-bottom: 24px;
}
h2 {
  font-size: 16px;
  margin: 20px 0 8px;
}
p,
ul {
  color: #555;
  font-size: 14px;
  line-height: 1.8;
}
ul {
  padding-left: 20px;
}
li {
  margin-bottom: 4px;
}
a {
  color: #1f8f7a;
}
.back {
  margin-top: 32px;
}
</style>
