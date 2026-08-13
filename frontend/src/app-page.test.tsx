import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Page from "@/app/page";

describe("Pagina inicial", () => {
  it("apresenta a entrada para o helpdesk", () => {
    render(<Page />);

    expect(
      screen.getByRole("heading", { name: "Fadex Helpdesk" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Acessar central" })
    ).toHaveAttribute("href", "/login");
  });
});
