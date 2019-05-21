import Vue from 'vue';
import './plugins/vuetify';
import App from './App.vue';
import axios from 'axios';
import VueAxios from 'vue-axios';
import moment from 'moment';
import JsonViewer from 'vue-json-viewer';

Vue.use(VueAxios, axios);
Vue.use(JsonViewer);
Vue.config.productionTip = false;
moment.locale('zh-cn');
Vue.prototype.$moment = moment;
new Vue({
  render: h => h(App)
}).$mount('#app');
