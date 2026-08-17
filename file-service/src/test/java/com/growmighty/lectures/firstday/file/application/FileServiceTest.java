package com.growmighty.lectures.firstday.file.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.file.domain.File;
import com.growmighty.lectures.firstday.file.domain.FileOwnerType;
import com.growmighty.lectures.firstday.file.domain.FileRepository;
import com.growmighty.lectures.firstday.file.infrastructure.S3PresignedUploadGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private S3PresignedUploadGenerator presignedUploadGenerator;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, presignedUploadGenerator);
    }

    @Test
    void 업로더_본인이면_삭제된다() {
        File file = File.register(FileOwnerType.PROJECT, 10L, 42L, "https://cdn.example.com/a.jpg", "a.jpg",
            "image/jpeg", 100L, 0);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));

        fileService.delete(1L, 42L);

        verify(fileRepository).deleteById(1L);
    }

    @Test
    void 업로더가_아니면_403으로_거부되고_삭제되지_않는다() {
        File file = File.register(FileOwnerType.PROJECT, 10L, 42L, "https://cdn.example.com/a.jpg", "a.jpg",
            "image/jpeg", 100L, 0);
        when(fileRepository.findById(1L)).thenReturn(Optional.of(file));

        assertThatThrownBy(() -> fileService.delete(1L, 999L))
            .isInstanceOf(BusinessException.class);
        verify(fileRepository, never()).deleteById(1L);
    }

    @Test
    void 존재하지_않는_파일이면_404다() {
        when(fileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.delete(1L, 42L))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
