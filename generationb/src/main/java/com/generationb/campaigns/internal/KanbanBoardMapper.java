package com.generationb.campaigns.internal;

import com.generationb.campaigns.*;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface KanbanBoardMapper {
    KanbanBoard toEntity(CreateBoardCommand command);
    BoardResponse toResponse(KanbanBoard board);
}
