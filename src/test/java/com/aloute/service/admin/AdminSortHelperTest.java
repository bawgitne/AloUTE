package com.aloute.service.admin;

import com.aloute.common.exception.ApiException;
import com.aloute.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminSortHelperTest {

    private static final Set<String> ALLOWED = AdminSortHelper.POST_SORT_FIELDS;
    private static final String DEFAULT_FIELD = "createdAt";

    @Test
    @DisplayName("parseSort with null or blank returns default descending sort")
    void parseSortNullOrBlank() {
        Sort sortNull = AdminSortHelper.parseSort(null, ALLOWED, DEFAULT_FIELD);
        assertThat(sortNull.getOrderFor(DEFAULT_FIELD)).isNotNull();
        assertThat(sortNull.getOrderFor(DEFAULT_FIELD).getDirection()).isEqualTo(Sort.Direction.DESC);

        Sort sortBlank = AdminSortHelper.parseSort("   ", ALLOWED, DEFAULT_FIELD);
        assertThat(sortBlank.getOrderFor(DEFAULT_FIELD)).isNotNull();
        assertThat(sortBlank.getOrderFor(DEFAULT_FIELD).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("parseSort with valid single property defaults to DESC")
    void parseSortSingleProperty() {
        Sort sort = AdminSortHelper.parseSort("id", ALLOWED, DEFAULT_FIELD);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("parseSort with valid property and direction asc/desc")
    void parseSortWithDirection() {
        Sort sortAsc = AdminSortHelper.parseSort("createdAt,asc", ALLOWED, DEFAULT_FIELD);
        assertThat(sortAsc.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);

        Sort sortDesc = AdminSortHelper.parseSort("createdAt,desc", ALLOWED, DEFAULT_FIELD);
        assertThat(sortDesc.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);

        Sort sortCaseInsensitive = AdminSortHelper.parseSort("createdAt,ASC", ALLOWED, DEFAULT_FIELD);
        assertThat(sortCaseInsensitive.getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @ParameterizedTest
    @ValueSource(strings = {",", ",,", " , ", "createdAt,", "createdAt,desc,", ",desc", "createdAt,desc,extra", "createdAt,invalidDir", "unknownField,desc"})
    @DisplayName("parseSort rejects malformed trailing separators, empty tokens, and invalid fields with controlled BAD_REQUEST")
    void parseSortRejectsMalformedInputs(String malformedSort) {
        assertThatThrownBy(() -> AdminSortHelper.parseSort(malformedSort, ALLOWED, DEFAULT_FIELD))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    @DisplayName("createPageable validates negative page and invalid sizes")
    void createPageableValidatesPageAndSize() {
        assertThatThrownBy(() -> AdminSortHelper.createPageable(-1, 20, "createdAt,desc", ALLOWED, DEFAULT_FIELD))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        assertThatThrownBy(() -> AdminSortHelper.createPageable(0, 0, "createdAt,desc", ALLOWED, DEFAULT_FIELD))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        assertThatThrownBy(() -> AdminSortHelper.createPageable(0, 101, "createdAt,desc", ALLOWED, DEFAULT_FIELD))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.BAD_REQUEST));

        Pageable valid = AdminSortHelper.createPageable(2, 50, "createdAt,asc", ALLOWED, DEFAULT_FIELD);
        assertThat(valid.getPageNumber()).isEqualTo(2);
        assertThat(valid.getPageSize()).isEqualTo(50);
        assertThat(valid.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
