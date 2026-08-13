package br.org.fadex.helpdesk.controller;

import br.org.fadex.helpdesk.model.choices.ChoicesResponse;
import br.org.fadex.helpdesk.service.ChoiceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/choices")
public class ChoicesController {

	private final ChoiceService choiceService;

	public ChoicesController(ChoiceService choiceService) {
		this.choiceService = choiceService;
	}

	@GetMapping
	public ChoicesResponse getChoices() {
		return choiceService.getChoices();
	}
}
