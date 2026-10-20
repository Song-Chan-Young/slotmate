package com.slotmate.instructor;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 수업 등록·수정 폼의 강사 선택용 목록 (명세 13.8). 등록·수정은 아직 DB 직접 입력 */
@RestController
public class AdminInstructorController {

    private final InstructorRepository instructorRepository;

    public AdminInstructorController(InstructorRepository instructorRepository) {
        this.instructorRepository = instructorRepository;
    }

    public record InstructorItem(Long instructorId, String name, String specialty, boolean active) {
    }

    /** 비활성 포함, 이름순 */
    @GetMapping("/api/v1/admin/instructors")
    @Transactional(readOnly = true)
    public List<InstructorItem> list() {
        return instructorRepository.findAll(Sort.by("name", "id")).stream()
                .map(i -> new InstructorItem(i.getId(), i.getName(), i.getSpecialty(), i.isActive()))
                .toList();
    }
}
