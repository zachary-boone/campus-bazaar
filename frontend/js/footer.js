Vue.component("footBar", {
  template: `
    <div class="foot">
    <div class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)">
      <div class="foot-view"><i class="el-icon-s-home"></i></div>
      <div class="foot-text">首页</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 2}" @click="toPage(2)">
      <div class="foot-view"><i class="el-icon-map-location"></i></div>
      <div class="foot-text">地图</div>
    </div>
    <div class="foot-box" @click="toPage(0)">
      <div class="foot-add">＋</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 3}" @click="toPage(3)">
      <div class="foot-view"><i class="el-icon-chat-dot-round"></i></div>
      <span class="foot-badge" v-if="msgUnread > 0">{{ msgUnread > 99 ? '99+' : msgUnread }}</span>
      <div class="foot-text">消息</div>
    </div>
    <div class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)">
      <div class="foot-view"><i class="el-icon-user"></i></div>
      <div class="foot-text">我的</div>
    </div>
  </div>
  `,
  data() {
    return {
      msgUnread: 0
    }
  },
  props: ['activeBtn'],
  created() {
    // 未读红点：只有登录了才查，避免每个页面都白打一次 401
    if (sessionStorage.getItem('token')) {
      axios.get('/messages/unread/count')
        .then(({ data }) => { this.msgUnread = Number(data) || 0; })
        .catch(() => {});
    }
  },
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/post-edit.html"
      } else if (i === 1) {
        location.href = "/"
      } else if (i === 2) {
        // 地图：原来这里没处理，导致底部「地图」点了没反应
        location.href = "/map.html"
      } else if (i === 3) {
        // 消息中心：需要登录，先判 token（401 会被 common.js 拦截跳登录，这里提前拦体验更好）
        if (!sessionStorage.getItem('token')) {
          if (typeof util !== 'undefined' && util.toast) {
            util.toast('请先登录', 'info')
          }
          location.href = "/login.html"
          return
        }
        location.href = "/message.html"
      } else if (i === 4) {
        location.href = "/info.html"
      }
    }
  }
})
