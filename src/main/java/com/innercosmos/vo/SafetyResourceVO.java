package com.innercosmos.vo;

public class SafetyResourceVO {
    public String id;
    public String label;
    public String phone;
    public String authorityUrl;
    public String verifiedAt;
    public String region;
    public String audience;
    public String hours;
    public String channel;
    public String category;

    public static SafetyResourceVO of(String id, String label, String phone, String authorityUrl,
                                      String verifiedAt, String region, String audience,
                                      String hours, String channel, String category) {
        SafetyResourceVO resource = new SafetyResourceVO();
        resource.id = id;
        resource.label = label;
        resource.phone = phone;
        resource.authorityUrl = authorityUrl;
        resource.verifiedAt = verifiedAt;
        resource.region = region;
        resource.audience = audience;
        resource.hours = hours;
        resource.channel = channel;
        resource.category = category;
        return resource;
    }
}
