package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.user.UserCreationDto;
import br.org.fadex.helpdesk.model.user.UserDto;
import br.org.fadex.helpdesk.model.user.UserFields;
import br.org.fadex.helpdesk.model.user.UserFilter;
import br.org.fadex.helpdesk.model.user.UserMinDto;
import br.org.fadex.helpdesk.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ResponseEntity<Page<UserMinDto>> findAll(
			@ModelAttribute UserFilter filter,
			@PageableDefault(size = 10, sort = UserFields.CREATED_AT, direction = Sort.Direction.DESC) Pageable pageable
	) {
		Page<UserMinDto> users = userService.findAll(filter, pageable);

		return ResponseEntity.ok(users);
	}

	@GetMapping("/{id}")
	public ResponseEntity<UserDto> findById(@PathVariable UUID id) {
		UserDto user = userService.findById(id);

		return ResponseEntity.ok(user);
	}

	@PostMapping
	public ResponseEntity<UserDto> create(@Valid @RequestBody UserCreationDto userCreationDto) {
		UserDto user = userService.create(userCreationDto);

		return ResponseEntity.status(HttpStatus.CREATED).body(user);
	}
}
