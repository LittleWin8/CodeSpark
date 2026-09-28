<template>
  <div id="privacyPage">
    <h1>隐私政策</h1>
    <p class="updated">最近更新：2026-09-22</p>

    <h2>一、站点性质</h2>
    <p>
      本站（{{ siteName }}）为个人学习与实践性质的非经营性网站，
      由站长个人运营，用于记录和展示人工智能应用开发的学习成果，免费向访问者开放体验，不涉及任何经营性服务。
    </p>

    <h2>二、我们收集的信息</h2>
    <ul>
      <li><strong>注册信息</strong>：您注册时提供的邮箱地址（用于发送验证码与找回密码），以及您自行设置的账号、昵称、头像等资料。</li>
      <li><strong>会话信息</strong>：登录状态下使用的会话 Cookie，用于保持您的登录状态，关闭浏览器后部分信息随之失效。</li>
      <li><strong>使用记录</strong>：您使用生成功能时产生的对话记录、生成的应用内容，以及用于统计资源消耗的用量数据。</li>
    </ul>
    <p>本站不收集任何与本站功能无关的个人信息，不进行用户画像，不推送商业广告。</p>

    <h2>三、信息用途</h2>
    <p>收集的信息仅用于：验证您的身份、维持账号登录、记录与统计资源用量、维护本站的正常运行。除此之外不会另作他用。</p>

    <h2>四、存储与保护</h2>
    <ul>
      <li>您的信息存储于本站服务器数据库与对象存储中，采用访问控制与加密等常规技术手段保护。</li>
      <li>本站不会向任何第三方出售您的个人信息。</li>
      <li>个人网站的防护能力有限，建议您不要在生成内容中输入敏感个人信息（如身份证号、银行卡、住址等）。</li>
    </ul>

    <h2>五、AI 生成内容说明</h2>
    <p>
      本站部分内容由人工智能模型生成，可能存在错误、遗漏或不准确之处，
      生成内容仅供参考，不构成任何专业建议。您应自行判断并承担基于生成内容开展活动的结果。
    </p>

    <h2>六、您的权利</h2>
    <ul>
      <li>您可以随时修改或删除自己创建的应用、对话等内容。</li>
      <li>如需删除账号或全部个人信息，可通过页面下方联系邮箱与站长取得联系。</li>
    </ul>

    <h2>七、政策的变更</h2>
    <p>本政策如有修订，将在本页面更新并标注日期。继续使用本站即视为您接受修订后的政策。</p>

    <h2>八、联系站长</h2>
    <p v-if="contactEmail">
      如有任何疑问，可通过邮箱联系：<a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
    </p>
    <p v-else>如有任何疑问，请通过站内提供的联系方式与站长联系。</p>

    <p class="back"><router-link to="/">← 返回首页</router-link></p>
  </div>
</template>

<script setup lang="ts">
import { contactEmail, siteName } from '@/config/site'
</script>

<style scoped>
#privacyPage {
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
