package com.aloute.dto.request;

import jakarta.validation.constraints.Size;
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

    @Size(max = 10, message = "A vote cannot contain more than 10 option IDs")
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
