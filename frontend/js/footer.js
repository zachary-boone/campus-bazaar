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
    }
  },
  props: ['activeBtn'],
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
        // 消息暂未实现，给个明确反馈，别让按钮像"坏了"
        if (typeof util !== 'undefined' && util.toast) {
          util.toast('消息功能即将上线', 'info')
        } else {
          alert('消息功能即将上线')
        }
      } else if (i === 4) {
        location.href = "/info.html"
      }
    }
  }
})