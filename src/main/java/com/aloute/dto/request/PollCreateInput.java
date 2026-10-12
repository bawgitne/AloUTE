package com.aloute.dto.request;

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
    private List<String> options;

    @Builder.Default
    private boolean allowMultiple = false;
    private Integer durationDays;
}
