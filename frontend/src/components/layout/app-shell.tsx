"use client";

import {
  BarChart3,
  ChevronLeft,
  ChevronRight,
  Home,
  LifeBuoy
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { Button } from "@/src/components/ui/button";
import { cn } from "@/src/lib/utils";
import { routes } from "@/src/routes/routes";

type AppShellProps = {
  children: React.ReactNode;
};

const navigationItems = [
  {
    href: routes.home,
    icon: Home,
    label: "Home"
  },
  {
    href: routes.tickets,
    icon: LifeBuoy,
    label: "Chamados"
  }
];

export function AppShell({ children }: AppShellProps) {
  const pathname = usePathname();
  const [isCollapsed, setIsCollapsed] = useState(false);

  return (
    <div className="min-h-screen bg-slate-50 text-slate-950">
      <aside
        className={cn(
          "fixed inset-y-0 left-0 z-40 hidden border-r border-slate-200 bg-white transition-[width] duration-200 lg:block",
          isCollapsed ? "w-20" : "w-64"
        )}
      >
        <div className="flex h-full flex-col">
          <div className="flex h-16 items-center justify-between border-b border-slate-200 px-4">
            <Link className="flex min-w-0 items-center gap-3" href={routes.home}>
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-emerald-700 text-white">
                <BarChart3 className="h-4 w-4" />
              </span>
              {!isCollapsed ? (
                <span className="truncate text-sm font-semibold">
                  Fadex Helpdesk
                </span>
              ) : null}
            </Link>

            <Button
              aria-label={isCollapsed ? "Expandir menu" : "Recolher menu"}
              size="icon"
              type="button"
              variant="ghost"
              onClick={() => setIsCollapsed((currentValue) => !currentValue)}
            >
              {isCollapsed ? (
                <ChevronRight className="h-4 w-4" />
              ) : (
                <ChevronLeft className="h-4 w-4" />
              )}
            </Button>
          </div>

          <nav className="grid gap-1 p-3">
            {navigationItems.map((item) => {
              const isActive =
                pathname === item.href || pathname.startsWith(`${item.href}/`);
              const Icon = item.icon;

              return (
                <Link
                  className={cn(
                    "flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors",
                    isActive
                      ? "bg-emerald-50 text-emerald-800"
                      : "text-slate-600 hover:bg-slate-100 hover:text-slate-950",
                    isCollapsed && "justify-center px-0"
                  )}
                  href={item.href}
                  key={item.href}
                  title={isCollapsed ? item.label : undefined}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {!isCollapsed ? <span>{item.label}</span> : null}
                </Link>
              );
            })}
          </nav>
        </div>
      </aside>

      <div
        className={cn(
          "min-h-screen transition-[padding] duration-200 lg:pl-64",
          isCollapsed && "lg:pl-20"
        )}
      >
        <header className="sticky top-0 z-30 flex h-14 items-center border-b border-slate-200 bg-white/95 px-4 backdrop-blur lg:hidden">
          <Link className="flex items-center gap-3" href={routes.home}>
            <span className="flex h-9 w-9 items-center justify-center rounded-md bg-emerald-700 text-white">
              <BarChart3 className="h-4 w-4" />
            </span>
            <span className="text-sm font-semibold">Fadex Helpdesk</span>
          </Link>
        </header>

        <main className="px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
