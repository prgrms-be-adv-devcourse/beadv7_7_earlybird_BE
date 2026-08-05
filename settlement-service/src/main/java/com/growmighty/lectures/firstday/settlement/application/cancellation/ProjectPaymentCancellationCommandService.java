package com.growmighty.lectures.firstday.settlement.application.cancellation;

import static com.growmighty.lectures.firstday.settlement.application.error.SettlementErrorCode.SETTLEMENT_DATA_INCONSISTENT;

import com.growmighty.lectures.firstday.settlement.application.error.SettlementException;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationResult;
import com.growmighty.lectures.firstday.settlement.application.port.payment.ProjectPaymentCancellationStatus;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommand;
import com.growmighty.lectures.firstday.settlement.domain.repository.ProjectPaymentCancellationCommandRepository;
import com.growmighty.lectures.firstday.settlement.domain.model.ProjectPaymentCancellationCommandStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectPaymentCancellationCommandService {

    private final ProjectPaymentCancellationCommandRepository repository;

    @Transactional(readOnly = true)
    public List<ProjectPaymentCancellationCommand> findAllByProjectIdIn(Set<Long> projectIds) {
        return repository.findAllByProjectIdIn(projectIds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProjectPaymentCancellationCommand> prepare(
            List<PrepareProjectPaymentCancellationCommand> requests
    ) {
        if (requests.isEmpty()) {
            return List.of();
        }
        validateUniqueRequests(requests);
        Set<Long> projectIds = requests.stream()
                .map(PrepareProjectPaymentCancellationCommand::projectId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, List<ProjectPaymentCancellationCommand>> existingByProjectId = repository
                .findAllByProjectIdIn(projectIds)
                .stream()
                .collect(Collectors.groupingBy(ProjectPaymentCancellationCommand::projectId));
        Map<Long, List<PrepareProjectPaymentCancellationCommand>> requestsByProjectId = requests.stream()
                .collect(Collectors.groupingBy(PrepareProjectPaymentCancellationCommand::projectId));

        List<ProjectPaymentCancellationCommand> commands = new ArrayList<>();
        List<ProjectPaymentCancellationCommand> newCommands = new ArrayList<>();
        for (Map.Entry<Long, List<PrepareProjectPaymentCancellationCommand>> entry
                : requestsByProjectId.entrySet()) {
            List<ProjectPaymentCancellationCommand> existing = existingByProjectId.get(entry.getKey());
            if (existing != null && !existing.isEmpty()) {
                requireSameCommands(existing, entry.getValue());
                commands.addAll(existing);
                continue;
            }
            entry.getValue().stream()
                    .map(request -> ProjectPaymentCancellationCommand.request(
                            request.projectId(),
                            request.orderId(),
                            request.reason(),
                            request.idempotencyKey()
                    ))
                    .forEach(newCommands::add);
        }
        commands.addAll(repository.saveAll(newCommands));
        Map<Long, ProjectPaymentCancellationCommand> commandByOrderId = commands.stream()
                .collect(Collectors.toMap(
                        ProjectPaymentCancellationCommand::orderId,
                        Function.identity()
                ));
        return requests.stream()
                .map(request -> commandByOrderId.get(request.orderId()))
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProjectPaymentCancellationCommand> recordResults(
            List<ProjectPaymentCancellationCommand> commands,
            List<ProjectPaymentCancellationResult> results
    ) {
        List<ProjectPaymentCancellationCommand> storedCommands = reload(commands);
        Map<Long, ProjectPaymentCancellationResult> resultByOrderId = validateResults(
                storedCommands,
                results
        );
        storedCommands.forEach(command -> command.record(toCommandStatus(
                resultByOrderId.get(command.orderId()).status()
        )));
        return repository.saveAll(storedCommands);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ProjectPaymentCancellationCommand> markUnknown(
            List<ProjectPaymentCancellationCommand> commands
    ) {
        List<ProjectPaymentCancellationCommand> storedCommands = reload(commands);
        storedCommands.forEach(command -> command.record(
                ProjectPaymentCancellationCommandStatus.UNKNOWN
        ));
        return repository.saveAll(storedCommands);
    }

    private List<ProjectPaymentCancellationCommand> reload(
            List<ProjectPaymentCancellationCommand> commands
    ) {
        Set<Long> projectIds = commands.stream()
                .map(ProjectPaymentCancellationCommand::projectId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, ProjectPaymentCancellationCommand> expectedByOrderId = commands.stream()
                .collect(Collectors.toMap(
                        ProjectPaymentCancellationCommand::orderId,
                        Function.identity()
                ));
        List<ProjectPaymentCancellationCommand> storedCommands = repository
                .findAllByProjectIdIn(projectIds)
                .stream()
                .filter(command -> expectedByOrderId.containsKey(command.orderId()))
                .toList();
        boolean mismatch = storedCommands.size() != commands.size()
                || storedCommands.stream().anyMatch(command -> {
                    ProjectPaymentCancellationCommand expected = expectedByOrderId.get(
                            command.orderId()
                    );
                    return expected == null
                            || !command.projectId().equals(expected.projectId())
                            || command.reason() != expected.reason()
                            || !command.idempotencyKey().equals(expected.idempotencyKey());
                });
        if (mismatch) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
        }
        return storedCommands;
    }

    private static void validateUniqueRequests(
            List<PrepareProjectPaymentCancellationCommand> requests
    ) {
        Set<Long> orderIds = new HashSet<>();
        Set<String> idempotencyKeys = new HashSet<>();
        boolean invalid = requests.stream().anyMatch(request -> request == null
                || !orderIds.add(request.orderId())
                || !idempotencyKeys.add(request.idempotencyKey()));
        if (invalid) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
        }
    }

    private static void requireSameCommands(
            List<ProjectPaymentCancellationCommand> existing,
            List<PrepareProjectPaymentCancellationCommand> requests
    ) {
        Map<Long, PrepareProjectPaymentCancellationCommand> requestByOrderId = requests.stream()
                .collect(Collectors.toMap(
                        PrepareProjectPaymentCancellationCommand::orderId,
                        Function.identity()
                ));
        boolean mismatch = existing.size() != requests.size()
                || existing.stream().anyMatch(command -> {
                    PrepareProjectPaymentCancellationCommand request = requestByOrderId.get(
                            command.orderId()
                    );
                    return request == null
                            || command.reason() != request.reason()
                            || !command.idempotencyKey().equals(request.idempotencyKey());
                });
        if (mismatch) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
        }
    }

    private static Map<Long, ProjectPaymentCancellationResult> validateResults(
            List<ProjectPaymentCancellationCommand> commands,
            List<ProjectPaymentCancellationResult> results
    ) {
        Set<Long> requestedOrderIds = commands.stream()
                .map(ProjectPaymentCancellationCommand::orderId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, ProjectPaymentCancellationResult> resultByOrderId = new HashMap<>();
        for (ProjectPaymentCancellationResult result : results) {
            if (result == null || resultByOrderId.put(result.orderId(), result) != null) {
                throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
            }
        }
        if (!resultByOrderId.keySet().equals(requestedOrderIds)) {
            throw new SettlementException(SETTLEMENT_DATA_INCONSISTENT);
        }
        return Map.copyOf(resultByOrderId);
    }

    private static ProjectPaymentCancellationCommandStatus toCommandStatus(
            ProjectPaymentCancellationStatus status
    ) {
        return ProjectPaymentCancellationCommandStatus.valueOf(status.name());
    }
}
