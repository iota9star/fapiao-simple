package io.nichijou.utils.fp

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URLEncoder
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random


class Fapiao private constructor() {
  private val gson by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    Gson()
  }

  private val normalClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val loggingInterceptor = HttpLoggingInterceptor()
    loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
    val cookieManager = CookieManager()
    cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL)
    val cookieJar = JavaNetCookieJar(cookieManager)
    val builder = OkHttpClient.Builder()
      .addNetworkInterceptor(loggingInterceptor)
      .readTimeout(30, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .connectTimeout(30, TimeUnit.SECONDS)
      .cookieJar(cookieJar)
      .retryOnConnectionFailure(true)
      .hostnameVerifier { _, _ -> true }
    val trustManager = object : X509TrustManager {
      @Throws(CertificateException::class)
      override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

      @Throws(CertificateException::class)
      override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

      override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf()
      }
    }
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    builder.sslSocketFactory(sslContext.socketFactory, trustManager)
    builder.build()
  }

  fun getVerCode(params: VerCodeParams): VerCodeWrapper? {
    val fpdm = params.fpdm
    val fphm = params.fphm
    val sm = System.currentTimeMillis() - Random.nextInt(1000) - 500// 尝试减去至少500毫秒时间，模拟浏览器加载過程中，jquery初始化
    val ts = sm + 1// 模拟第一次加载的时间戳
    val callback = "jQuery1102${Math.random().toString().replace(".", "")}_$sm"
    val now = System.currentTimeMillis()
    val publicKey = getVerCodePublicKey(fpdm, now.toString())
    val area = getFapiaoArea(fpdm)
    val url = VER_CODE_URL +
      "?callback=$callback" +
      "&fpdm=$fpdm" +
      "&fphm=$fphm" +
      "&r=${Math.random()}" +
      "&v=V1.0.07_001" +
      "&nowtime=$now" +
      "&area=${area.code}" +
      "&publickey=$publicKey" +
      "&_=$ts"
    log("getVerCode: $url")
    val request = Request.Builder()
      .get()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .header("Referer", HOME)
      .header("Accept", "*/*")
      .header("Accept-Language", "zh-CN,zh;q=0.9")
      .header("Cache-Control", "no-cache")
      .header("Connection", "keep-alive")
      .header("Pragma", "no-cache")
      .header("DNT", "1")
      .header("Host", area.url.split(":")[1].replace("//", ""))
      .build()
    val response = normalClient.newCall(request).execute()
    if (response.isSuccessful) {
      val result = response.body()?.string()
      if (!result.isNullOrBlank()) {
        val matcher = Pattern.compile("^jQuery\\d+_\\d+\\((.*)\\)$").matcher(result)
        if (matcher.find()) {
          val json = matcher.group(1)
          val resp = gson.fromJson<FapiaoResponse>(json, FapiaoResponse::class.java)
          val verCode = VerCode(
            img = "data:image/png;base64,${resp.key1}",
            type = resp.key4,
            hint = VerCodeType.getHintByCode(resp.key4),
            date = resp.key2
          )
          val extra = FapiaoReqExtra(
            callback = callback,
            index = resp.key3,
            oldWeb = resp.key5
          )
          return VerCodeWrapper(verCode, extra, area)
        }
      }
    }
    return null
  }

  fun getFapiao(params: FapiaoParams): FapiaoWrapper? {
    val type = getFapiaoType(params.fpdm)
    val area = getFapiaoArea(params.fpdm)
    val publicKey =
      getFapiaoPublicKey(params.fpdm, params.fphm, params.kprq, params.extra, params.yzmsj, params.yzm)
    val callback = params.callback
    val ts = callback.split("_")[1].toLong() + 2
    val url = if (params.oldWeb == "1") {
      "${area.url}/WebQuery/invQuery" +
        "?callback=$callback" +
        "&fpdm=${params.fpdm}" +
        "&fphm=${params.fphm}" +
        "&kprq=${params.kprq}" +
        "&fpje=${params.extra}" +
        "&fplx=${type.code}" +
        "&yzm=${params.yzm}" +
        "&yzmSj=${params.yzmsj.urlEncode()}" +
        "&index=${params.index}" +
        "&area=${area.code}" +
        "&publickey=$publicKey" +
        "&_=$ts"
    } else {
      "${area.url}/WebQuery/vatQuery" +
        "?callback=$callback" +
        "&key1=${params.fpdm}" +
        "&key2=${params.fphm}" +
        "&key3=${params.kprq}" +
        "&key4=${params.extra}" +
        "&fplx=${type.code}" +
        "&yzm=${params.yzm}" +
        "&yzmSj=${params.yzmsj.urlEncode()}" +
        "&index=${params.index}" +
        "&area=${area.code}" +
        "&publickey=$publicKey" +
        "&_=$ts"
    }
    log("getFapiao: $url")
    val verCode = HttpUrl.parse(VER_CODE_URL) ?: return null
    val cookies = normalClient.cookieJar().loadForRequest(verCode).map { Cookie.parse(verCode, it.toString()) }
    normalClient.cookieJar().saveFromResponse(HttpUrl.parse(url) ?: return null, cookies)
    val request = Request.Builder()
      .get()
      .url(url)
      .header("User-Agent", USER_AGENT)
      .header("Referer", HOME)
      .header("Accept", "*/*")
      .header("Accept-Language", "zh-CN,zh;q=0.9")
      .header("Cache-Control", "no-cache")
      .header("Connection", "keep-alive")
      .header("Pragma", "no-cache")
      .header("DNT", "1")
      .header("Host", area.url.split(":")[1].replace("//", ""))
      .build()
    val response = normalClient.newCall(request).execute()
    if (response.isSuccessful) {
      val result = response.body()?.string()
      if (!result.isNullOrBlank()) {
        val matcher = Pattern.compile("^jQuery\\d+_\\d+\\((.*)\\)$").matcher(result)
        if (matcher.find()) {
          val resp = gson.fromJson<FapiaoResponse>(matcher.group(1), FapiaoResponse::class.java)
          return FapiaoWrapper(RespCode.getRespCode(resp.key1).msg, resp)
        }
      }
    }
    return null
  }

  companion object {
    private const val VER_CODE_URL = "https://fpcy.gd-n-tax.gov.cn/WebQuery/yzmQuery"
    private val SPECIAL_CODES = arrayOf("144031539110", "131001570151", "133011501118", "111001571071")
    private const val USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.108 Safari/537.36"
    private const val HOME = "https://inv-veri.chinatax.gov.cn/"
    private val Areas by lazy {
      mutableListOf(
        Area("北京", "1100", "https://fpcy.beijing.chinatax.gov.cn:443"),
        Area("天津", "1200", "https://fpcy.tjsat.gov.cn:443"),
        Area("河北", "1300", "https://fpcy.hebei.chinatax.gov.cn"),
        Area("山西", "1400", "https://fpcy.shanxi.chinatax.gov.cn:443"),
        Area("内蒙古", "1500", "https://fpcy.neimenggu.chinatax.gov.cn:443"),
        Area("辽宁", "2100", "https://fpcy.liaoning.chinatax.gov.cn:443"),
        Area("大连", "2102", "https://fpcy.dlntax.gov.cn:443"),
        Area("吉林", "2200", "https://fpcy.jilin.chinatax.gov.cn:4432"),
        Area("黑龙江", "2300", "https://fpcy.hl-n-tax.gov.cn:443"),
        Area("上海", "3100", "https://fpcyweb.tax.sh.gov.cn:1001"),
        Area("江苏", "3200", "https://fpcy.jiangsu.chinatax.gov.cn:80"),
        Area("浙江", "3300", "https://fpcy.zhejiang.chinatax.gov.cn:443"),
        Area("宁波", "3302", "https://fpcy.ningbo.chinatax.gov.cn:443"),
        Area("安徽", "3400", "https://fpcy.anhui.chinatax.gov.cn:443"),
        Area("福建", "3500", "https://fpcy.fujian.chinatax.gov.cn:443"),
        Area("厦门", "3502", "https://fpcy.xiamen.chinatax.gov.cn"),
        Area("江西", "3600", "https://fpcy.jiangxi.chinatax.gov.cn:82"),
        Area("山东", "3700", "https://fpcy.shandong.chinatax.gov.cn:443"),
        Area("青岛", "3702", "https://fpcy.qingdao.chinatax.gov.cn:443"),
        Area("河南", "4100", "https://fpcy.henan.chinatax.gov.cn"),
        Area("湖北", "4200", "https://fpcy.hb-n-tax.gov.cn:443"),
        Area("湖南", "4300", "https://fpcy.hunan.chinatax.gov.cn:8083"),
        Area("广东", "4400", "https://fpcy.gd-n-tax.gov.cn:443"),
        Area("深圳", "4403", "https://fpcy.shenzhen.chinatax.gov.cn:443"),
        Area("广西", "4500", "https://fpcy.guangxi.chinatax.gov.cn:8200"),
        Area("海南", "4600", "https://fpcy.hainan.chinatax.gov.cn:443"),
        Area("重庆", "5000", "https://fpcy.chongqing.chinatax.gov.cn:80"),
        Area("四川", "5100", "https://fpcy.sichuan.chinatax.gov.cn:443"),
        Area("贵州", "5200", "https://fpcy.gz-n-tax.gov.cn:80"),
        Area("云南", "5300", "https://fpcy.yngs.gov.cn:443"),
        Area("西藏", "5400", "https://fpcy.xztax.gov.cn:81"),
        Area("陕西", "6100", "https://fpcy.shaanxi.chinatax.gov.cn:443"),
        Area("甘肃", "6200", "https://fpcy.gansu.chinatax.gov.cn:443"),
        Area("青海", "6300", "https://fpcy.qinghai.chinatax.gov.cn:443"),
        Area("宁夏", "6400", "https://fpcy.ningxia.chinatax.gov.cn:443"),
        Area("新疆", "6500", "https://fpcy.xj-n-tax.gov.cn:443")
      )
    }
    private val jsEnv by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      var path = Fapiao::class.java.classLoader.getResource("core.js").path
      if (path.matches(Regex("/[A-Za-z0-9]+:/.*"))) {
        path = path.substring(1)
      }
      File(path).readText()
    }
    @Volatile
    private var instance: Fapiao? = null

    @JvmStatic
    fun getFapiaoArea(fpdm: String): Area {
      var area = if (fpdm.length == 12) {
        fpdm.substring(1, 5)
      } else {
        fpdm.substring(0, 4)
      }
      if (area != "2102" && area != "3302" && area != "3502" && area != "3702" && area != "4403") {
        area = area.substring(0, 2) + "00"
      }
      for (a in Areas) {
        if (a.code == area) return a
      }
      throw AreaNotExistErrorException("区域代码不正确：$fpdm => $area")
    }

    @JvmStatic
    fun getFapiaoType(fpdm: String): FapiaoType {
      val b: String
      var co = "99"
      if (fpdm.length == 12) {
        b = fpdm.substring(7, 8)
        for (s in SPECIAL_CODES) {
          if (s == fpdm) {
            co = "10"
            continue
          }
        }
        if (co == "99") {
          if (fpdm[0] == '0' && fpdm.substring(10, 12) == "11") {
            co = "10"
          }
          if (fpdm[0] == '0' && (fpdm.substring(10, 12) == "04" || fpdm.substring(10, 12) == "05")) {
            co = "04"
          }
          if (fpdm[0] == '0' && (fpdm.substring(10, 12) == "06" || fpdm.substring(10, 12) == "07")) {
            co = "11"
          }
          if (fpdm[0] == '0' && fpdm.substring(10, 12) == "12") {
            co = "14"
          }
        }
        if (co == "99") {
          if (fpdm.substring(10, 12) == "17" && fpdm[0] == '0') {
            co = "15"
          }
          if (co == "99" && b == "2" && fpdm[0] != '0') {
            co = "03"
          }
        }
      } else if (fpdm.length == 10) {
        b = fpdm.substring(7, 8)
        if (b == "1" || b == "5") {
          co = "01"
        } else if (b == "6" || b == "3") {
          co = "04"
        } else if (b == "7" || b == "2") {
          co = "02"
        }
      }
      var desc = "未知"
      if (co == "02") {
        desc = "合计金额"
      } else if (co == "03") {
        desc = "税价"
      } else if (co == "15") {
        desc = "车价"
      } else if (co == "04" || co == "10" || co == "11" || co == "14") {
        desc = "校验码"
      } else if (co == "01" || co == "99") {
        desc = "开具金额"
      }
      return FapiaoType(co, desc)
    }

    @JvmStatic
    fun getInstance(): Fapiao {
      return instance ?: synchronized(this) {
        instance ?: Fapiao().also { instance = it }
      }
    }

    fun getVerCodePublicKey(code: String, nowTime: String): String {
      try {
        val jsContext = Context.enter()
        val jsScope = jsContext.initStandardObjects()
        jsContext.optimizationLevel = -1
        jsContext.evaluateString(jsScope, jsEnv, null, 1, null)
        val func = jsScope.get("getVerCodePublicKey", jsScope)
        if (func is Function) {
          val my = func.call(jsContext, jsScope, jsScope, arrayOf(code, nowTime))
            ?: throw PublicKeyException("获取验证码PublicKey错误")
          log("获得发票【 $code 】验证码PublicKey：$my")
          return my.toString()
        }
      } finally {
        Context.exit()
      }
      throw PublicKeyException("获取验证码PublicKey错误")
    }

    fun getFapiaoPublicKey(
      fpdm: String,
      fphm: String,
      kprq: String,
      kjje: String,
      yzmSj: String,
      yzm: String
    ): String {
      try {
        val jsContext = Context.enter()
        val jsScope = jsContext.initStandardObjects()
        jsContext.optimizationLevel = -1
        jsContext.evaluateString(jsScope, jsEnv, null, 1, null)
        val func = jsScope.get("getFapiaoPublicKey", jsScope)
        if (func is Function) {
          val my = func.call(jsContext, jsScope, jsScope, arrayOf(fpdm, fphm, kprq, kjje, yzmSj, yzm))
            ?: throw PublicKeyException("获取发票PublicKey错误")
          log("获得发票【 $fpdm 】PublicKey：$my")
          return my.toString()
        }
      } finally {
        Context.exit()
      }
      throw PublicKeyException("获取发票PublicKey错误")
    }
  }
}

class PublicKeyException(message: String?) : RuntimeException(message)
class UnknownVerCodeException(message: String?) : RuntimeException(message)
class AreaNotExistErrorException(message: String?) : RuntimeException(message)

data class FapiaoParams(
  val yzm: String,
  val yzmsj: String,
  val callback: String,
  val fpdm: String,
  val fphm: String,
  val kprq: String,
  val extra: String,
  val index: String,
  val oldWeb: String
)

data class VerCodeParams(
  val fpdm: String,
  val fphm: String
)

data class FapiaoResponse(
  @SerializedName("key1")
  val key1: String,
  @SerializedName("key2")
  val key2: String,
  @SerializedName("key3")
  val key3: String,
  @SerializedName("key4")
  val key4: String,
  @SerializedName("key5")
  val key5: String
)

enum class VerCodeType(private val code: String, private val hint: String) {
  NORMAL("00", "请输入验证码文字"),
  RED("01", "请输入验证码图片中红色文字"),
  YELLOW("02", "请输入验证码图片中黄色文字"),
  BLUE("03", "请输入验证码图片中蓝色文字");

  companion object {
    @JvmStatic
    fun getHintByCode(code: String): String {
      for (value in values()) {
        if (value.code == code) {
          return value.hint
        }
      }
      throw UnknownVerCodeException("未知的验证码类型：$code")
    }
  }
}

enum class RespCode(val code: String, val msg: String) {
  CODE_1("1", "该省尚未开通发票查验功能"),
  CODE_001("001", "成功"),
  CODE_002("002", "超过该张发票当日查验次数（请于次日再次查验）"),
  CODE_003("003", "发票查验请求太频繁，请稍后再试"),
  CODE_004("004", "超过服务器最大请求数，请稍后访问"),
  CODE_005("005", "请求不合法"),
  CODE_006("006", "不一致"),
  CODE_007("007", "验证码失效"),
  CODE_008("008", "验证码错误"),
  CODE_009("009", "查无此票"),
  CODE_010("010", "系统异常，请重试"),
  CODE_010_("010_", "系统异常，请重试（05）"),
  CODE_016("016", "服务器接收的请求太频繁，请稍后再试"),
  CODE_020("020", "由于查验行为异常，涉嫌违规，当前无法使用查验服务"),
  CODE_rqerr("rqerr", "当日开具发票可于次日进行查验"),
  CODE_other("", "系统异常，请重试！(04)");

  companion object {
    fun getRespCode(code: String): RespCode {
      for (value in values()) {
        if (value.code == code) return value
      }
      return CODE_other
    }
  }
}

data class FapiaoType(
  val code: String,
  val desc: String
)

data class VerCodeWrapper(
  @SerializedName("verCode")
  val verCode: VerCode,
  @SerializedName("extra")
  val extra: FapiaoReqExtra,
  @SerializedName("area")
  val area: Area
)

data class FapiaoWrapper(
  val msg: String,
  val resp: FapiaoResponse
)

data class VerCode(
  @SerializedName("img")
  val img: String,
  @SerializedName("type")
  val type: String,
  @SerializedName("hint")
  val hint: String,
  @SerializedName("date")
  val date: String
)

data class FapiaoReqExtra(
  @SerializedName("callback")
  val callback: String,
  @SerializedName("index")
  val index: String,
  @SerializedName("oldWeb")
  val oldWeb: String = "0"
)

data class Area(
  @SerializedName("name")
  val name: String,
  @SerializedName("code")
  val code: String,
  @SerializedName("url")
  val url: String
)

inline fun <reified T> T.log(msg: String) {
  println("${T::class.java.name} => $msg")
}

fun String.urlEncode(): String? {
  return URLEncoder.encode(this)
}

