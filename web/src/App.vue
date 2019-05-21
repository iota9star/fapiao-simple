<template xmlns:v-slot="http://www.w3.org/1999/XSL/Transform">
  <v-app id="inspire">
    <v-toolbar app>
      <v-toolbar-title class="headline text-uppercase">
        <span>国家税务总局全国增值税发票查验平台</span>
      </v-toolbar-title>
      <v-spacer/>
      <v-btn flat href="https://github.com/iota9star/fapiao-simple" target="_blank">
        <span class="mr-2">GITHUB</span>
      </v-btn>
    </v-toolbar>
    <v-content>
      <v-container fluid fill-height>
        <v-layout justify-center>
          <v-flex xs12 sm8 md4>
            <v-card>
              <v-toolbar dark color="primary">
                <v-toolbar-title>全国增值税发票查验平台</v-toolbar-title>
                <v-spacer/>
              </v-toolbar>
              <v-card-text>
                <v-form>
                  <v-text-field
                    label="发票代码"
                    type="text"
                    outline
                    :counter="12"
                    required
                    ref="fpdm"
                    :hint=" info.area && info.area.name ? `当前发票来自：${info.area.name}` : '' "
                    @blur="getFapiaoInfo"
                    persistent-hint
                    :rules="rules.fpdm"
                    v-model="form.fpdm"></v-text-field>
                  <v-text-field
                    label="发票号码"
                    type="text"
                    :rules="rules.fphm"
                    ref="fphm"
                    outline
                    required
                    v-model="form.fphm"></v-text-field>
                  <v-dialog
                    ref="dialog"
                    v-model="kprqPK"
                    :return-value.sync="kprq"
                    persistent
                    lazy
                    full-width
                    width="290px">
                    <template v-slot:activator="{ on }">
                      <v-text-field
                        v-model="kprq"
                        label="开票日期"
                        readonly
                        :rules="rules.kprq"
                        outline
                        ref="kprq"
                        required
                        v-on="on"></v-text-field>
                    </template>
                    <v-date-picker
                      v-model="kprq"
                      scrollable>
                      <v-spacer/>
                      <v-btn
                        flat
                        color="primary"
                        @click="kprqPK = false">
                        取消
                      </v-btn>
                      <v-btn
                        flat
                        color="primary"
                        @click="$refs.dialog.save(kprq)">
                        确认
                      </v-btn>
                    </v-date-picker>
                  </v-dialog>
                  <v-text-field
                    :label=" info.type && info.type.desc ? info.type.desc : '开具金额' "
                    type="text"
                    outline
                    required
                    ref="extra"
                    v-model="form.extra"></v-text-field>
                  <v-text-field
                    label="验证码"
                    outline
                    :hint="verCode && verCode.verCode && verCode.verCode.hint ? verCode.verCode.hint:'请刷新验证码'"
                    required
                    type="text"
                    persistent-hint
                    ref="yzm"
                    v-model="form.yzm">
                    <template v-slot:append>
                      <v-icon @click="getVerCode">
                        refresh
                      </v-icon>
                    </template>
                    <template
                      v-slot:append-outer
                      v-if="verCode && verCode.verCode && verCode.verCode.img">
                      <img
                        class="ver-code"
                        :src="verCode.verCode.img"
                        @click="getVerCode"
                        alt="验证码">
                    </template>
                  </v-text-field>
                </v-form>
              </v-card-text>
              <v-card-actions>
                <v-spacer/>
                <v-btn color="primary" @click="getFapiao" class="query">
                  查询
                </v-btn>
              </v-card-actions>
            </v-card>
          </v-flex>
          <div style="width: 24px"></div>
          <v-flex xs12 sm8 md4>
            <v-card>
              <v-toolbar dark color="primary">
                <v-toolbar-title>发票基础信息响应</v-toolbar-title>
                <v-spacer/>
              </v-toolbar>
              <json-viewer :value="info" copyable boxed sort></json-viewer>
            </v-card>
            <v-card style="margin-top: 24px">
              <v-toolbar dark color="primary">
                <v-toolbar-title>验证码响应</v-toolbar-title>
                <v-spacer/>
              </v-toolbar>
              <json-viewer :value="verCode" copyable boxed sort></json-viewer>
            </v-card>
            <v-card style="margin-top: 24px">
              <v-toolbar dark color="primary">
                <v-toolbar-title>查询发票信息响应</v-toolbar-title>
                <v-spacer/>
              </v-toolbar>
              <json-viewer :value="fapiao" copyable boxed sort></json-viewer>
            </v-card>
          </v-flex>
        </v-layout>
      </v-container>
    </v-content>
    <v-snackbar v-model="snackbar">
      {{ msg }}
      <v-btn
        color="pink"
        flat
        @click="snackbar = false">
        关闭
      </v-btn>
    </v-snackbar>
  </v-app>
</template>

<script>
  export default {
    name: 'App',
    components: {},
    data: () => ({
      kprqPK: false,
      snackbar: false,
      msg: '',
      kprq: '',
      form: {
        yzm: '',
        yzmsj: '',
        callback: '',
        fpdm: '',
        fphm: '',
        kprq: '',
        extra: '',
        index: '',
        oldWeb: ''
      },
      verCode: {},
      fapiao: {},
      info: {},
      rules: {
        fpdm: [val => ((val || '').length === 10 || (val || '').length === 12) || '发票代码长度为10或12'],
        fphm: [val => (val || '').length > 0 || '发票号码不能为空'],
        kprq: [val => (val || '').length > 0 || '请选择开票日期']
      }
    }),
    watch: {
      kprq(value) {
        this.form.kprq = this.$moment(value).format('YYYYMMDD');
      }
    },
    methods: {
      getFapiaoInfo() {
        let fpdm = this.form.fpdm;
        if (fpdm && (fpdm.length === 10 || fpdm.length === 12)) {
          this.info = {};
          this.$http.get(`/fi?fpdm=${fpdm}`).then((res) => {
            if (res.status === 200) {
              this.info = res.data;
            }
          });
        }
      },
      getVerCode() {
        this.$http.post('/vc', {
          fpdm: this.form.fpdm,
          fphm: this.form.fphm
        }).then((res) => {
          if (res.status === 200) {
            this.verCode = res.data;
          }
        });
      },
      getFapiao() {
        this.form.yzmsj = this.verCode.verCode.date;
        this.form.callback = this.verCode.extra.callback;
        this.form.index = this.verCode.extra.index;
        this.form.oldWeb = this.verCode.extra.oldWeb;
        this.form.kprq = this.$moment(this.kprq).format('YYYYMMDD');
        this.$http.post('/fp', this.form).then((res) => {
          if (res.status === 200) {
            let data = res.data;
            this.fapiao = data;
            this.msg = data.msg;
            this.snackbar = true;
          }
        });
      }
    }
  };
</script>
<style lang="stylus">
  .ver-code
    border-radius 4px
    height 58px

  .v-text-field.v-text-field--enclosed .v-input__append-outer
    margin-top 0
    margin-bottom 0

  .v-card__actions > *, .v-card__actions .v-btn
    margin 8px
</style>
