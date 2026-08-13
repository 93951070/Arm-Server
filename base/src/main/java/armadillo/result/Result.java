package armadillo.result;

import armadillo.enums.LanguageEnums;
import armadillo.utils.SysConfigUtil;
import com.alibaba.fastjson.annotation.JSONField;

import java.util.Objects;


public class Result {
    private int code;
    private String msg;
    private Object data;
    @JSONField(serialize = false)
    private final LanguageEnums languageEnums;
    @JSONField(serialize = false)
    private final ResultBasic resultBasic;

    public Result(ResultBasic resultBasic, Object data, LanguageEnums languageEnums) {
        this.resultBasic = resultBasic;
        this.code = resultBasic.getCode();
        String langMsg = SysConfigUtil.getLanguageConfigUtil(languageEnums, resultBasic.getConfig());
        this.msg = langMsg != null ? langMsg : resultBasic.getConfig();
        this.data = data;
        this.languageEnums = languageEnums;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public Object getData() {
        return data;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Result setMsg(Object... msg) {
        String langMsg = SysConfigUtil.getLanguageConfigUtil(languageEnums, resultBasic.getConfig());
        String template = langMsg != null ? langMsg : resultBasic.getConfig();
        try {
            this.msg = String.format(template, msg);
        } catch (Exception e) {
            this.msg = template;
        }
        return this;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
