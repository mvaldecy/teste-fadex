import type { Metadata } from "next";
import { Toaster } from "@/src/components/ui/sonner";
import "./globals.css";

export const metadata: Metadata = {
  title: "Fadex Helpdesk",
  description: "Central de chamados internos com triagem inteligente"
};

export default function RootLayout({
  children
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="pt-BR">
      <body>
        {children}
        <Toaster position="top-right" richColors />
      </body>
    </html>
  );
}
