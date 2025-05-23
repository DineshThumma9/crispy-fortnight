package com.pm.jujutsu.service;

import com.pm.jujutsu.dtos.ProjectRequestDTO;
import com.pm.jujutsu.dtos.ProjectResponseDTO;
import com.pm.jujutsu.exceptions.NotFoundException;
import com.pm.jujutsu.exceptions.UnauthorizedException;
import com.pm.jujutsu.mappers.ProjectMapper;
import com.pm.jujutsu.model.Project;
import com.pm.jujutsu.model.User;
import com.pm.jujutsu.repository.ProjectRepository;
import com.pm.jujutsu.repository.UserRepository;
import com.pm.jujutsu.utils.JwtUtil;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ProjectService {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    public ProjectResponseDTO createProject(ProjectRequestDTO projectRequestDTO) {
        ObjectId currentUserId = jwtUtil.getCurrentUser().getId();

        Project project = projectMapper.toEntity(projectRequestDTO);
        project.setOwnerId(currentUserId);

        Project savedProject = projectRepository.save(project);
        return enrichProjectResponse(savedProject);
    }

    public ProjectResponseDTO updateProject(String projectId, ProjectRequestDTO projectRequestDTO) {
        ObjectId objectId = new ObjectId(projectId);
        ObjectId currentUserId = jwtUtil.getCurrentUser().getId();

        Project project = projectRepository.findById(objectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        if (!project.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedException("Not authorized to update this project");
        }

        project.setTitle(projectRequestDTO.getTitle());
        project.setDescription(projectRequestDTO.getDescription());
        project.setTechRequirements(projectRequestDTO.getTechRequirements());
        project.setPrivate(projectRequestDTO.isPrivate());
        project.setGithubLink(projectRequestDTO.getGithubLink());

        Project savedProject = projectRepository.save(project);
        return enrichProjectResponse(savedProject);
    }

    public ProjectResponseDTO getProject(String projectId) {
        ObjectId objectId = new ObjectId(projectId);
        Project project = projectRepository.findById(objectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        return enrichProjectResponse(project);
    }

    public boolean deleteProject(String projectId) {
        ObjectId objectId = new ObjectId(projectId);
        ObjectId currentUserId = jwtUtil.getCurrentUser().getId();

        Project project = projectRepository.findById(objectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));

        if (!project.getOwnerId().equals(currentUserId)) {
            throw new UnauthorizedException("Not authorized to delete this project");
        }

        projectRepository.deleteById(objectId);
        return true;
    }

    // Helper method to reduce code duplication
    private ProjectResponseDTO enrichProjectResponse(Project project) {
        ProjectResponseDTO responseDTO = projectMapper.toResponseEntity(project);

        // Add owner details
        Optional<User> owner = userRepository.findById(project.getOwnerId());
        if (owner.isPresent()) {
            responseDTO.setOwnerUsername(owner.get().getUsername());
            responseDTO.setOwnerProfilePicUrl(owner.get().getProfilePicUrl());

            // Add current contributors
            if (project.getCurrentContributorIds() != null && !project.getCurrentContributorIds().isEmpty()) {
                List<String> currentContributorNames = project.getCurrentContributorIds().stream()
                        .filter(id -> id != null)
                        .map(id -> userRepository.findById(id))
                        .filter(Optional::isPresent)
                        .map(user -> user.get().getUsername())
                        .collect(Collectors.toList());
                responseDTO.setCurrentContributors(currentContributorNames);
            }

            // Add past contributors
            if (project.getPastContributorIds() != null && !project.getPastContributorIds().isEmpty()) {
                List<String> pastContributorNames = project.getPastContributorIds().stream()
                        .filter(id -> id != null)
                        .map(id -> userRepository.findById(id))
                        .filter(Optional::isPresent)
                        .map(user -> user.get().getUsername())
                        .collect(Collectors.toList());
                responseDTO.setPastContributors(pastContributorNames);
            }
        }

        return responseDTO;
    }
}
