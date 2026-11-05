{
  description = "The ElevenLabs CLI, its static release build";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/c27cdad491a991b11ed731760aa2ef8db0cb0410";

  outputs =
    { nixpkgs, ... }:
    let
      version = "1.8.0";
      builds = {
        aarch64-linux = {
          arch = "aarch64";
          hash = "sha256-TuZxly3P8xysOL+eHBTeYphflgzvqoAhIon71NiVyYU=";
        };
        x86_64-linux = {
          arch = "x86_64";
          hash = "sha256-KTQDnPj0LVriSjZHMf27/5FTpeBNYQtoEj/xJ+Q2FG8=";
        };
      };
    in
    {
      packages = builtins.mapAttrs (
        system: build:
        let
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          default = pkgs.stdenvNoCC.mkDerivation {
            pname = "elevenlabs-cli";
            inherit version;
            src = pkgs.fetchurl {
              url = "https://github.com/elevenlabs/cli/releases/download/v${version}/elevenlabs-cli-${build.arch}-unknown-linux-musl.tar.gz";
              inherit (build) hash;
            };
            installPhase = "install -D --mode 755 elevenlabs $out/bin/elevenlabs";
          };
        }
      ) builds;
    };
}
