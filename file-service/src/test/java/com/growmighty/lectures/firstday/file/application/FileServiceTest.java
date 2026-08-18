package com.growmighty.lectures.firstday.file.application;

import com.growmighty.lectures.firstday.common.exception.BusinessException;
import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.file.application.dto.RegisterFileCommand;
import com.growmighty.lectures.firstday.file.application.port.ProjectPort;
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

    @Mock
    private ProjectPort projectPort;

    private static final String CDN_BASE_URL = "https://cdn.example.com";

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, presignedUploadGenerator, projectPort, CDN_BASE_URL);
    }

    @Test
    void 프로젝트_소유자가_등록하면_성공한다() {
        RegisterFileCommand command = new RegisterFileCommand(FileOwnerType.PROJECT, 10L, 42L,
            CDN_BASE_URL + "/files/42/a.jpg", "a.jpg", "image/jpeg", 100L, 0);
        when(projectPort.getCreatorId(10L)).thenReturn(42L);
        when(fileRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.register(command);
    }

    @Test
    void 프로젝트_소유자가_아니면_등록이_403으로_거부된다() {
        RegisterFileCommand command = new RegisterFileCommand(FileOwnerType.PROJECT, 10L, 999L,
            CDN_BASE_URL + "/files/999/a.jpg", "a.jpg", "image/jpeg", 100L, 0);
        when(projectPort.getCreatorId(10L)).thenReturn(42L);

        assertThatThrownBy(() -> fileService.register(command))
            .isInstanceOf(BusinessException.class);
        verify(fileRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 본인_presign_키_형식이_아닌_storedUrl은_등록이_거부된다() {
        RegisterFileCommand command = new RegisterFileCommand(FileOwnerType.PROJECT, 10L, 42L,
            "https://attacker.example.com/malware.exe", "a.jpg", "image/jpeg", 100L, 0);

        assertThatThrownBy(() -> fileService.register(command))
            .isInstanceOf(BusinessException.class);
        verify(fileRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(projectPort, never()).getCreatorId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 다른_사람의_presign_키_경로를_가리키는_storedUrl은_등록이_거부된다() {
        // 자기 자신이 presign 받은 URL이 아니라 남의 uploaderId 경로를 그대로 베낀 storedUrl.
        RegisterFileCommand command = new RegisterFileCommand(FileOwnerType.PROJECT, 10L, 42L,
            CDN_BASE_URL + "/files/999/a.jpg", "a.jpg", "image/jpeg", 100L, 0);

        assertThatThrownBy(() -> fileService.register(command))
            .isInstanceOf(BusinessException.class);
        verify(fileRepository, never()).save(org.mockito.ArgumentMatchers.any());
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
