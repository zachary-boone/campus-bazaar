// let commonURL = "http://192.168.50.115:8081";
let commonURL = "/api";
// 设置后台服务地址
axios.defaults.baseURL = commonURL;
axios.defaults.timeout = 2000;
// request 拦截器：每次请求实时读 token (登录前/登录后都能正确注入)
axios.interceptors.request.use(
  config => {
    const token = sessionStorage.getItem("token");
    if (token) config.headers['authorization'] = token;
    return config;
  },
  error => {
    console.log(error);
    return Promise.reject(error);
  }
);
axios.interceptors.response.use(function (response) {
  // 判断执行结果
  if (!response.data.success) {
    return Promise.reject(response.data.errorMsg)
  }
  return response.data;
}, function (error) {
  // 一般是服务端异常或者网络异常
  console.log(error)
  // 注意：超时/断网时 error.response 是 undefined，直接取 .status 会抛 TypeError，
  // 这里统一判空，避免把"请求超时"也报成"服务器异常"
  const status = error && error.response && error.response.status;
  if(status == 401){
    // 跳登录前先收掉弹层，避免遮罩残留在页面上
    util.closeAllPopups();
    setTimeout(() => {
      location.href = "/login.html"
    }, 200);
    return Promise.reject("请先登录");
  }
  if (error && error.code === 'ECONNABORTED') {
    return Promise.reject("请求超时，请重试");
  }
  return Promise.reject("服务器异常");
});
/**
 * 查询参数序列化
 * <p>
 * 注意：<b>只丢弃真正"没有值"的参数</b>（undefined / null / 空串）。
 * 原来的写法是 `if (params[k])`，会把 <b>0 和 false 一起丢掉</b>，
 * 导致 `typeId=0`（表示"全部分类"）发不出去 → 后端必填校验失败、
 * 首页/分类页/发帖页的商品列表全空。
 * <p>
 * 另外自定义了 paramsSerializer 之后，axios 不再做 URL 编码，所以要自己 encode
 * （否则 area=东校区 这类中文会以原始字节发出去）。
 */
axios.defaults.paramsSerializer = function(params) {
  const parts = [];
  Object.keys(params || {}).forEach(function(k) {
    const v = params[k];
    if (v === undefined || v === null || v === '') return;
    parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
  });
  return parts.join('&');
}
const util = {
  commonURL,
  getUrlParam(name) {
    let reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
    let r = window.location.search.substr(1).match(reg);
    if (r != null) {
      return decodeURI(r[2]);
    }
    return "";
  },
  formatPrice(val) {
    if (typeof val === 'string') {
      if (isNaN(val)) {
        return null;
      }
      // 价格转为整数
      const index = val.lastIndexOf(".");
      let p = "";
      if (index < 0) {
        // 无小数
        p = val + "00";
      } else if (index === p.length - 2) {
        // 1位小数
        p = val.replace("\.", "") + "0";
      } else {
        // 2位小数
        p = val.replace("\.", "")
      }
      return parseInt(p);
    } else if (typeof val === 'number') {
      if (!val) {
        return null;
      }
      const s = val + '';
      if (s.length === 0) {
        return "0.00";
      }
      if (s.length === 1) {
        return "0.0" + val;
      }
      if (s.length === 2) {
        return "0." + val;
      }
      const i = s.indexOf(".");
      if (i < 0) {
        return s.substring(0, s.length - 2) + "." + s.substring(s.length - 2)
      }
      const num = s.substring(0, i) + s.substring(i + 1);
      if (i === 1) {
        // 1位整数
        return "0.0" + num;
      }
      if (i === 2) {
        return "0." + num;
      }
      if (i > 2) {
        return num.substring(0, i - 2) + "." + num.substring(i - 2)
      }
    }
  },

  /**
   * 收口页面上所有 Element 弹层，并清理可能残留的遮罩。
   *
   * 为什么要它：Element UI 的 MessageBox（$alert/$confirm）是"单例栈 + 共享 .v-modal"，
   * 重复或并发打开时，容易出现「上一个弹窗关不掉，一直悬浮」以及「遮罩留在页面上
   * 导致整页点不动」。所以每次要弹新框前先统一关掉旧的，跳转前也关一次。
   */
  closeAllPopups() {
    const proto = (window.Vue && Vue.prototype) || {};
    try {
      if (proto.$msgbox && typeof proto.$msgbox.close === 'function') proto.$msgbox.close();
    } catch (e) { }
    try {
      if (proto.$message && typeof proto.$message.closeAll === 'function') proto.$message.closeAll();
    } catch (e) { }
    try {
      if (proto.$notify && typeof proto.$notify.closeAll === 'function') proto.$notify.closeAll();
    } catch (e) { }
    // 兜底：异常路径下 Element 可能没摘掉遮罩，留着会让整页无法点击
    const modal = document.querySelector('.v-modal');
    if (modal && modal.parentNode) modal.parentNode.removeChild(modal);
  },

  /**
   * 纯信息提示：自动消失、不阻塞页面、重复调用不会堆一屏。
   * 需要用户做选择时才用 $confirm / $alert。
   * @param {string} message 文案
   * @param {string} type    success / warning / error / info
   */
  toast(message, type) {
    const M = window.Vue && Vue.prototype && Vue.prototype.$message;
    if (!M) return;
    this.closeAllPopups();
    const fn = (type && typeof M[type] === 'function') ? M[type] : M;
    fn.call(M, { message: message, duration: 3000, grouping: true, showClose: true });
  },

  /**
   * 带操作入口的提示：同样自动消失（Element Notification 支持 duration），
   * 比 $alert 更适合"告诉你结果，顺带给个跳转"的场景。
   * @param {object} opts { title, message, type, duration, onClick }
   */
  notify(opts) {
    const N = window.Vue && Vue.prototype && Vue.prototype.$notify;
    if (!N) { this.toast((opts && opts.message) || '', (opts && opts.type) || 'success'); return; }
    this.closeAllPopups();
    const type = (opts && opts.type) || 'success';
    const fn = (typeof N[type] === 'function') ? N[type] : N;
    const opt = {
      title: (opts && opts.title) || '提示',
      message: (opts && opts.message) || '',
      duration: (opts && opts.duration) || 4500,
      position: 'top-right',
      onClick: opts && opts.onClick
    };
    if (!opt.onClick) delete opt.onClick;
    fn.call(N, opt);
  }
};

/**
 * 全局兜底：给 Element 的"轻提示"补上自动消失与不堆叠。
 *
 * 背景（真实踩到的 bug）：Element 的 $message / $notify
 *  1) duration: 0 表示"永不自动关闭"——一旦有代码（或以后新增代码）传了 0，
 *     提示就会一直悬浮在页面上；
 *  2) 默认会向上堆叠，连点按钮或轮询场景下会积一屏，看起来也像"关不掉"。
 * 这里在原型上包一层，统一：先把旧提示关掉 → 补默认 duration=3000/4500 →
 * 开启 grouping（同文案合并）与关闭按钮。
 *
 * 只动轻提示，不动 $alert / $confirm（那两个本就需要用户点击确认，不做自动关闭）。
 * 依赖：element.js 必须先于 common.js 加载（本项目所有页面都满足）。
 */
(function patchElementLightTips() {
  if (typeof Vue === 'undefined' || !Vue.prototype) return;

  /** 把 duration:0（永不关闭）兜成默认值，0 是 Element 约定为"常驻"的值 */
  const fixOptions = function (options, defaultDuration, forcedType) {
    let opt = options;
    if (typeof opt === 'string') {
      opt = { message: opt };
    } else {
      opt = Object.assign({}, opt || {});
    }
    if (forcedType) opt.type = forcedType;
    if (opt.duration === undefined || opt.duration === null || opt.duration === 0) {
      opt.duration = defaultDuration;
    }
    return opt;
  };

  // ---------- $message ----------
  const M = Vue.prototype.$message;
  if (typeof M === 'function') {
    const closeAllMessage = function () {
      try { if (typeof M.closeAll === 'function') M.closeAll(); } catch (e) { }
    };
    const wrap = function (options) {
      closeAllMessage();
      const opt = fixOptions(options, 3000);
      if (opt.grouping === undefined) opt.grouping = true;
      if (opt.showClose === undefined) opt.showClose = true;
      return M(opt);
    };
    ['success', 'warning', 'error', 'info'].forEach(function (type) {
      if (typeof M[type] === 'function') {
        wrap[type] = function (options) {
          return wrap(fixOptions(options, 3000, type));
        };
      }
    });
    wrap.closeAll = closeAllMessage;
    wrap.close = function (id) { try { if (typeof M.close === 'function') M.close(id); } catch (e) { } };
    Vue.prototype.$message = wrap;
  }

  // ---------- $notify ----------
  const N = Vue.prototype.$notify;
  if (typeof N === 'function') {
    const closeAllNotify = function () {
      try { if (typeof N.closeAll === 'function') N.closeAll(); } catch (e) { }
    };
    const wrapNotify = function (options) {
      closeAllNotify();
      if (!options || typeof options !== 'object') options = { message: options || '' };
      return N(fixOptions(options, 4500));
    };
    ['success', 'warning', 'error', 'info'].forEach(function (type) {
      if (typeof N[type] === 'function') {
        wrapNotify[type] = function (options) {
          return wrapNotify(fixOptions(options, 4500, type));
        };
      }
    });
    wrapNotify.closeAll = closeAllNotify;
    Vue.prototype.$notify = wrapNotify;
  }
})();
