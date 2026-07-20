package prac.fin.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BrowserInfoRequest {
    private boolean javaEnabled;
    private boolean javascriptEnabled;
    private int     timezoneOffset;
    private int     colorDepth;
    private int     screenWidth;
    private int     screenHeight;
    private String  userAgent;
    private String  language;
}
