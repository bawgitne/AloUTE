package com.aloute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VotePollRequest {

    private List<Long> optionIds;
    private Long optionId;

    public List<Long> getEffectiveOptionIds() {
        if (optionIds != null && !optionIds.isEmpty()) {
            return optionIds;
        }
        if (optionId != null) {
            return List.of(optionId);
        }
        return List.of();
    }
}
