"use client";

import { LogOut, User } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/src/components/ui/button";
import { routes } from "@/src/routes/routes";
import { useSessionStore } from "@/src/stores/session.store";

function toInitials(name: string) {
  const parts = name.trim().split(/\s+/).slice(0, 2);
  const initials = parts
    .map((part) => part.charAt(0).toUpperCase())
    .join("");

  return initials || "?";
}

export function UserMenu() {
  const router = useRouter();
  const user = useSessionStore((state) => state.user);
  const role = useSessionStore((state) => state.role);
  const logout = useSessionStore((state) => state.logout);
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    function handlePointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        setIsOpen(false);
      }
    }

    document.addEventListener("mousedown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  if (!user) {
    return null;
  }

  function handleLogout() {
    setIsOpen(false);
    logout();
    router.replace(routes.login);
  }

  return (
    <div className="relative" ref={containerRef}>
      <button
        aria-expanded={isOpen}
        aria-haspopup="menu"
        className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors hover:bg-slate-100"
        onClick={() => setIsOpen((currentValue) => !currentValue)}
        type="button"
      >
        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-emerald-700 text-xs font-semibold text-white">
          {toInitials(user.name)}
        </span>
        <span className="hidden max-w-[10rem] truncate font-medium text-slate-800 sm:block">
          {user.name}
        </span>
      </button>

      {isOpen ? (
        <div
          className="absolute right-0 z-50 mt-2 w-60 rounded-md border border-slate-200 bg-white p-2 shadow-lg"
          role="menu"
        >
          <div className="flex items-start gap-2 px-2 py-2">
            <User className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-950">
                {user.name}
              </p>
              <p className="text-xs text-slate-500">{role ?? "Sem perfil"}</p>
            </div>
          </div>

          <div className="my-1 h-px bg-slate-200" />

          <Button
            className="w-full justify-start"
            onClick={handleLogout}
            role="menuitem"
            size="sm"
            type="button"
            variant="ghost"
          >
            <LogOut className="h-4 w-4" />
            Sair
          </Button>
        </div>
      ) : null}
    </div>
  );
}
