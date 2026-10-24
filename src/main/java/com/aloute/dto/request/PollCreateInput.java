package com.aloute.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class PollCreateInput {

    @NotBlank(message = "Poll question must not be blank")
    @Size(max = 255, message = "Poll question must not exceed 255 characters")
    private String question;

    @NotEmpty(message = "Poll must contain at least 2 options")
    @Size(min = 2, max = 10, message = "Poll must contain between 2 and 10 options")
    private List<@NotBlank(message = "Poll option must not be blank")
            @Size(max = 200, message = "Poll option must not exceed 200 characters") String> options;

    @Builder.Default
    private boolean allowMultiple = false;

    @Min(value = 1, message = "Poll duration must be at least 1 day")
    @Max(value = 365, message = "Poll duration must not exceed 365 days")
    private Integer durationDays;
}
