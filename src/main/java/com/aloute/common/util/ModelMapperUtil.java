package com.aloute.common.util;

import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for object mapping using ModelMapper.
 * Used directly within Service layers to map between Entity and DTO.
 */
public final class ModelMapperUtil {

    private static final ModelMapper modelMapper;

    static {
        modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(MatchingStrategies.STRICT)
                .setSkipNullEnabled(true);
    }

    private ModelMapperUtil() {
        // Private constructor to prevent instantiation
    }

    public static ModelMapper getModelMapper() {
        return modelMapper;
    }

    /**
     * Maps single source object to a new instance of target class.
     */
    public static <D, T> D map(final T entity, Class<D> outClass) {
        if (entity == null) {
            return null;
        }
        return modelMapper.map(entity, outClass);
    }

    /**
     * Maps a collection of source objects to a list of target class instances.
     */
    public static <D, T> List<D> mapList(final Collection<T> entityList, Class<D> outClass) {
        if (entityList == null || entityList.isEmpty()) {
            return Collections.emptyList();
        }
        return entityList.stream()
                .map(entity -> map(entity, outClass))
                .collect(Collectors.toList());
    }

    /**
     * Maps properties from source object into an existing destination object.
     */
    public static <S, D> void map(final S source, D destination) {
        if (source != null && destination != null) {
            modelMapper.map(source, destination);
        }
    }
}
