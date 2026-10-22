package com.aloute.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public class UpdateProfileRequest {
    @Size(max = 100)
    private String displayName;
    @Size(max = 500)
    private String bio;
    private String website;
    private Long avatarMediaId;
    private Long coverMediaId;
    @JsonProperty("isPrivate")
    private Boolean privateAccount;

    private boolean displayNamePresent;
    private boolean bioPresent;
    private boolean websitePresent;
    private boolean avatarMediaIdPresent;
    private boolean coverMediaIdPresent;
    private boolean privateAccountPresent;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayNamePresent = true; this.displayName = displayName; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bioPresent = true; this.bio = bio; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.websitePresent = true; this.website = website; }
    public Long getAvatarMediaId() { return avatarMediaId; }
    public void setAvatarMediaId(Long avatarMediaId) { this.avatarMediaIdPresent = true; this.avatarMediaId = avatarMediaId; }
    public Long getCoverMediaId() { return coverMediaId; }
    public void setCoverMediaId(Long coverMediaId) { this.coverMediaIdPresent = true; this.coverMediaId = coverMediaId; }
    @JsonProperty("isPrivate") public Boolean getPrivateAccount() { return privateAccount; }
    @JsonProperty("isPrivate") public void setPrivateAccount(Boolean privateAccount) { this.privateAccountPresent = true; this.privateAccount = privateAccount; }
    public boolean hasDisplayName() { return displayNamePresent; }
    public boolean hasBio() { return bioPresent; }
    public boolean hasWebsite() { return websitePresent; }
    public boolean hasAvatarMediaId() { return avatarMediaIdPresent; }
    public boolean hasCoverMediaId() { return coverMediaIdPresent; }
    public boolean hasPrivateAccount() { return privateAccountPresent; }
}
