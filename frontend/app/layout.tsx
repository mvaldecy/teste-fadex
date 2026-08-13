import type { Metadata } from "next";
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
      <body>{children}</body>
    </html>
  );
}
